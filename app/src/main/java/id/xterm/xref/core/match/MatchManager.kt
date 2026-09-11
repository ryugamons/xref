package id.xterm.xref.core.match

import id.xterm.xref.core.websocket.KickEvent
import id.xterm.xref.data.repository.WebSocketRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

sealed class MatchState {
    data object Idle : MatchState()
    data class Running(val timeRemainingMillis: Long) : MatchState()
    data object Ended : MatchState()
    data class Result(val winner: String) : MatchState()
}

data class MatchParticipant(
    val id: String,
    val hasVoted: Boolean = false
)

data class MatchSide(
    val name: String,
    val participants: List<MatchParticipant>
)

@Singleton
class MatchManager @Inject constructor(
    private val webSocketRepository: WebSocketRepository
) {
    companion object {
        private var instance: MatchManager? = null
        fun getInstance(webSocketRepository: WebSocketRepository): MatchManager {
            if (instance == null) instance = MatchManager(webSocketRepository)
            return instance!!
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<MatchState>(MatchState.Idle)
    val state = _state.asStateFlow()

    private val _teamA = MutableStateFlow(MatchSide("Team A", emptyList()))
    val teamA = _teamA.asStateFlow()

    private val _teamB = MutableStateFlow(MatchSide("Team B", emptyList()))
    val teamB = _teamB.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _kickCountMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val kickCountMap = _kickCountMap.asStateFlow()

    private val _kickHistory = MutableStateFlow<List<Long>>(emptyList())
    val kickHistory = _kickHistory.asStateFlow()

    private val _kps = MutableStateFlow(0f)
    val kps = _kps.asStateFlow()

    private var timerJob: Job? = null
    private var lastKickTime = 0L

    init {
        scope.launch {
            webSocketRepository.events.collect { jsonObject ->
                val type = jsonObject["type"]?.jsonPrimitive?.content
                if (type == "room.kick") {
                    try {
                        val event = json.decodeFromJsonElement<KickEvent>(jsonObject)
                        handleKickEvent(event)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (type == "room.message") {
                    val from = jsonObject["data"]?.jsonObject?.get("from")?.jsonPrimitive?.content ?: "System"
                    val text = jsonObject["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                    _logs.value = (listOf("$from: $text") + _logs.value).take(100)
                }
            }
        }
        
        // Update KPS every second
        scope.launch {
            while (isActive) {
                updateKps()
                delay(1000)
            }
        }
    }

    fun startMatch(teamAIds: List<String>, teamBIds: List<String>) {
        _teamA.value = MatchSide("Team A", teamAIds.map { MatchParticipant(it) })
        _teamB.value = MatchSide("Team B", teamBIds.map { MatchParticipant(it) })
        _logs.value = listOf("Match Started: ${teamAIds.size}vs${teamBIds.size}")
        _state.value = MatchState.Running(3000L)
        _kickCountMap.value = emptyMap()
        _kickHistory.value = emptyList()
        lastKickTime = System.currentTimeMillis()
        resetTimer()
    }

    private fun handleKickEvent(event: KickEvent) {
        val from = event.data.from
        val target = event.data.target
        val timestamp = System.currentTimeMillis()
        
        // Track kick for stats
        _kickCountMap.update { current ->
            current.toMutableMap().apply {
                this[from] = (this[from] ?: 0) + 1
            }
        }
        _kickHistory.update { (it + timestamp).filter { ts -> ts > timestamp - 60000 } }

        if (_state.value !is MatchState.Running) return

        val diff = (timestamp - lastKickTime) / 1000f
        lastKickTime = timestamp

        val logEntry = "[${String.format("%.1fs", diff)}] $from Kicked $target"
        _logs.value = (listOf(logEntry) + _logs.value).take(100)

        // Update participant vote status
        updateVoteStatus(from)

        // Reset timer
        resetTimer()

        // Check if a side can no longer vote
        checkEndConditions()
    }

    private fun updateKps() {
        val now = System.currentTimeMillis()
        val window = 5000L // 5 seconds average
        val kicksInWindow = _kickHistory.value.count { it > now - window }
        _kps.value = kicksInWindow / (window / 1000f)
    }

    private fun updateVoteStatus(id: String) {
        _teamA.update { side ->
            side.copy(participants = side.participants.map { 
                if (it.id == id) it.copy(hasVoted = true) else it 
            })
        }
        _teamB.update { side ->
            side.copy(participants = side.participants.map { 
                if (it.id == id) it.copy(hasVoted = true) else it 
            })
        }
    }

    private fun checkEndConditions() {
        val teamACanVote = _teamA.value.participants.any { !it.hasVoted }
        val teamBCanVote = _teamB.value.participants.any { !it.hasVoted }

        if (!teamACanVote || !teamBCanVote) {
            val reason = if (!teamACanVote) "Team A out of votes" else "Team B out of votes"
            endMatch(reason)
        }
    }

    private fun resetTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            val startTime = System.currentTimeMillis()
            val duration = 3000L
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = duration - elapsed
                if (remaining <= 0) {
                    _state.value = MatchState.Running(0)
                    endMatch("Timeout: Target not kicked")
                    break
                }
                _state.value = MatchState.Running(remaining)
                delay(16) // ~60fps for smooth countdown
            }
        }
    }

    private fun endMatch(reason: String) {
        timerJob?.cancel()
        _state.value = MatchState.Ended
        _logs.value = (listOf("Match Ended: $reason") + _logs.value).take(50)
    }

    fun stopMatch() {
        timerJob?.cancel()
        _state.value = MatchState.Idle
    }
}
