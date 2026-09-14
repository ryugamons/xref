package id.xterm.xref.core.match

import id.xterm.xref.data.repository.WebSocketRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

sealed class MatchState {
    data object Idle : MatchState()
    data object Running : MatchState()
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

class MatchSession(val room: String) {
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

    private var lastKickTime = 0L

    fun start(teamAIds: List<String>, teamBIds: List<String>) {
        _teamA.value = MatchSide("Team A", teamAIds.map { MatchParticipant(it) })
        _teamB.value = MatchSide("Team B", teamBIds.map { MatchParticipant(it) })
        _logs.value = listOf("Match Started in $room: ${teamAIds.size}vs${teamBIds.size}")
        _state.value = MatchState.Running
        _kickCountMap.value = emptyMap()
        lastKickTime = System.currentTimeMillis()
    }

    fun handleKick(from: String, body: String) {
        val timestamp = System.currentTimeMillis()
        
        _kickCountMap.update { current ->
            current.toMutableMap().apply {
                this[from] = (this[from] ?: 0) + 1
            }
        }

        if (_state.value !is MatchState.Running) return

        val diff = (timestamp - lastKickTime) / 1000f
        lastKickTime = timestamp

        val logEntry = "[${String.format("%.1fs", diff)}] $from Action: $body"
        _logs.value = (listOf(logEntry) + _logs.value).take(100)

        updateVoteStatus(from)
        checkEndConditions()
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

    private fun endMatch(reason: String) {
        _state.value = MatchState.Ended
        _logs.value = (listOf("Match Ended: $reason") + _logs.value).take(50)
    }

    fun stop() {
        _state.value = MatchState.Idle
    }
}

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
    private val sessions = ConcurrentHashMap<String, MatchSession>()

    private val _activeSessions = MutableStateFlow<List<String>>(emptyList())
    val activeSessions = _activeSessions.asStateFlow()

    init {
        scope.launch {
            webSocketRepository.events.collect { jsonObject ->
                val type = jsonObject["type"]?.jsonPrimitive?.content
                if (type == "room.message.received" || type == "room.message") {
                    val room = jsonObject["room"]?.jsonPrimitive?.content?.lowercase() ?: ""
                    val from = jsonObject["username"]?.jsonPrimitive?.content ?: "System"
                    val body = jsonObject["body"]?.jsonPrimitive?.content ?: ""
                    
                    if (body.contains("kicked", ignoreCase = true)) {
                         sessions[room]?.handleKick(from, body)
                    }
                } else if (type == "room.participant.removed") {
                    val room = jsonObject["room"]?.jsonPrimitive?.content?.lowercase() ?: ""
                    val username = jsonObject["username"]?.jsonPrimitive?.content?.lowercase() ?: ""
                    
                    sessions[room]?.let { session ->
                        val isParticipant = session.teamA.value.participants.any { it.id.lowercase() == username } ||
                                           session.teamB.value.participants.any { it.id.lowercase() == username }
                        
                        if (isParticipant) {
                            stopMatch(room)
                        }
                    }
                }
            }
        }
    }

    fun startMatch(room: String, teamAIds: List<String>, teamBIds: List<String>) {
        val normalizedRoom = room.lowercase()
        val session = sessions.getOrPut(normalizedRoom) { 
            MatchSession(normalizedRoom) 
        }
        session.start(teamAIds, teamBIds)
        updateActiveSessions()
    }

    fun getSession(room: String): MatchSession? {
        return sessions[room.lowercase()]
    }

    private fun updateActiveSessions() {
        _activeSessions.value = sessions.keys().toList()
    }

    fun stopMatch(room: String) {
        val normalizedRoom = room.lowercase()
        sessions[normalizedRoom]?.stop()
        sessions.remove(normalizedRoom)
        updateActiveSessions()
    }
}
