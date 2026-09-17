package id.xterm.xref.core.match

import android.util.Log
import id.xterm.xref.data.repository.WebSocketRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.Calendar
import java.util.TimeZone

sealed class MatchState {
    data object Idle : MatchState()
    data object Kickoff : MatchState()
    data object Battle : MatchState() 
    data object Ending : MatchState() 
    data object Ended : MatchState()  
}

data class MatchParticipant(
    val id: String,
    var isPresent: Boolean = true,
    var isKicked: Boolean = false
)

data class KickLog(
    val team: String, // "A" or "B"
    val username: String,
    val timeMs: Long,
    val action: String // "KICKED" or "FAILED"
)

data class MatchSide(
    val name: String,
    val participants: List<MatchParticipant>
)

class MatchSession(
    val room: String,
    private val webSocketRepository: WebSocketRepository,
    private val onMatchFinished: (MatchManager.MatchResult) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _state = MutableStateFlow<MatchState>(MatchState.Idle)
    val state = _state.asStateFlow()

    private val _teamA = MutableStateFlow(MatchSide("TEAM A", emptyList()))
    val teamA = _teamA.asStateFlow()

    private val _teamB = MutableStateFlow(MatchSide("TEAM B", emptyList()))
    val teamB = _teamB.asStateFlow()

    private var currentPhase: String = "MATCH"
    
    private val _kickLogs = MutableStateFlow<List<KickLog>>(emptyList())
    val kickLogs = _kickLogs.asStateFlow()

    private val _kickCountMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val kickCountMap = _kickCountMap.asStateFlow()

    private var battleStartTime = 0L
    private var voteTimer: Job? = null
    private var postKickTimer: Job? = null
    private var lockJob: Job? = null
    
    private var isGoalSent = false
    private var endReason: String = ""
    
    private var scoreSnapshotA = 0
    private var scoreSnapshotB = 0

    fun start(phase: String, nameA: String, teamAIds: List<String>, nameB: String, teamBIds: List<String>) {
        currentPhase = phase
        _teamA.value = MatchSide(nameA, teamAIds.map { MatchParticipant(it) })
        _teamB.value = MatchSide(nameB, teamBIds.map { MatchParticipant(it) })
        _kickLogs.value = emptyList()
        _state.value = MatchState.Kickoff
        _kickCountMap.value = emptyMap()
        isGoalSent = false
        endReason = ""
        battleStartTime = 0L
        scoreSnapshotA = 0
        scoreSnapshotB = 0
        Log.d("XREF_MATCH", "Match Session Initialized in $room: $nameA vs $nameB")

        // Auto-lock room 30s after Starter starts (Kickoff phase)
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(30000)
            if (_state.value == MatchState.Kickoff || _state.value == MatchState.Battle) {
                webSocketRepository.sendMessage(room, "/lock", "REFEREE")
            }
        }
    }

    private fun parseServerTimeMs(serverMessageId: String?): Long {
        if (serverMessageId == null || !serverMessageId.startsWith("msg_")) return System.currentTimeMillis()
        return try {
            val raw = serverMessageId.substring(4) // Skip "msg_"
            val parts = raw.split(".")
            val tsPart = parts[0] // YYYYMMDDHHMMSS
            val fracPart = if (parts.size > 1) parts[1] else "0"

            // 1. Parse the main timestamp (seconds precision)
            val sdf = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val baseMs = sdf.parse(tsPart)?.time ?: 0L
            
            // 2. Parse the fractional part correctly as milliseconds
            // Treat fracPart as a decimal: .1 -> 100ms, .01 -> 10ms, .001 -> 1ms, .10912 -> 109ms
            val ms = if (fracPart.isNotEmpty()) {
                val fraction = "0.$fracPart".toDouble()
                (fraction * 1000).toLong()
            } else 0L

            baseMs + ms
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun handleMessage(body: String, serverMessageId: String?) {
        val serverNow = parseServerTimeMs(serverMessageId)
        
        // 1. Detect Battle Start (Kickoff Countdown Failed)
        if (_state.value == MatchState.Kickoff && body.contains("Failed to kick", ignoreCase = true)) {
            _state.value = MatchState.Battle
            battleStartTime = serverNow
            Log.d("XREF_MATCH", "Battle Started in $room at ServerTime: $serverNow")
            return
        }

        // 2. Goal confirmation
        if (body.contains("GOALLLLLLLLLL", ignoreCase = true)) {
            if (_state.value == MatchState.Battle || _state.value == MatchState.Ending) {
                // Cancel lock timer if match ends early
                lockJob?.cancel()
                
                // Snapshot the OFFICIAL score exactly at the moment of GOAL
                scoreSnapshotA = _teamA.value.participants.count { !it.isKicked }
                scoreSnapshotB = _teamB.value.participants.count { !it.isKicked }
                
                _state.value = MatchState.Ended
                calculateAndBroadcastResults()
            }
            return
        }

        // 3. Battle Logic
        if (_state.value == MatchState.Idle || _state.value == MatchState.Kickoff) return

        // Detect Vote
        if (body.contains("A vote to kick", ignoreCase = true) && body.contains("started by", ignoreCase = true)) {
            postKickTimer?.cancel()
            voteTimer?.cancel()
            voteTimer = scope.launch {
                delay(3000)
                if (_state.value == MatchState.Battle) sendGoal("TIMEOUT (VOTE HANG)")
            }
        }

        // Detect Kicked
        if (body.contains("has been kicked", ignoreCase = true)) {
            val kickedUser = body.split(" ")[0].trim().lowercase()
            voteTimer?.cancel()
            markKicked(kickedUser)
            postKickTimer?.cancel()
            postKickTimer = scope.launch {
                delay(3000)
                if (_state.value == MatchState.Battle) sendGoal("TIMEOUT (NO RESPONSE)")
            }
            val diff = if (battleStartTime > 0) serverNow - battleStartTime else 0L
            val team = getTeam(kickedUser)
            if (team != "Unknown") {
                val log = KickLog(team, kickedUser, diff, "KICKED")
                _kickLogs.update { (listOf(log) + it).take(100) }
            }
        }

        // Vote Failed
        if (body.contains("Failed to kick", ignoreCase = true) && _state.value == MatchState.Battle) {
            val targetUser = body.replace("Failed to kick ", "", ignoreCase = true).trim().lowercase()
            voteTimer?.cancel()
            val diff = if (battleStartTime > 0) serverNow - battleStartTime else 0L
            val team = getTeam(targetUser)
            if (team != "Unknown") {
                val log = KickLog(team, targetUser, diff, "FAILED")
                _kickLogs.update { (listOf(log) + it).take(100) }
            }
        }
    }

    fun handleUserLeft(username: String) {
        if (_state.value != MatchState.Battle) return
        val user = username.lowercase(); val team = getTeam(user)
        if (team != "Unknown") sendGoal("SUSPEND ($team)")
    }

    private fun markKicked(username: String) {
        val user = username.lowercase()
        _teamA.update { side -> side.copy(participants = side.participants.map { if (it.id.lowercase() == user) it.copy(isKicked = true) else it }) }
        _teamB.update { side -> side.copy(participants = side.participants.map { if (it.id.lowercase() == user) it.copy(isKicked = true) else it }) }
    }

    private fun getTeam(username: String): String {
        val user = username.lowercase()
        if (_teamA.value.participants.any { it.id.lowercase() == user }) return "A"
        if (_teamB.value.participants.any { it.id.lowercase() == user }) return "B"
        return "Unknown"
    }

    private fun sendGoal(reason: String) {
        if (isGoalSent) return
        isGoalSent = true; endReason = reason; _state.value = MatchState.Ending
        webSocketRepository.sendDirect(room, "/goal")
    }

    private fun calculateAndBroadcastResults() {
        scope.launch {
            delay(3000) 
            val sideA = _teamA.value; val sideB = _teamB.value
            val isSuspendA = endReason.contains("SUSPEND (A)"); val isSuspendB = endReason.contains("SUSPEND (B)")
            val finalA = if (isSuspendA) 0 else scoreSnapshotA; val finalB = if (isSuspendB) 0 else scoreSnapshotB
            
            val winnerName = when {
                isSuspendA -> sideB.name; isSuspendB -> sideA.name
                finalA > finalB -> sideA.name; finalB > finalA -> sideB.name
                else -> "DRAW"
            }
            
            val scoreAStr = if (isSuspendA) "[SUSPEND]" else "[$finalA]"
            val scoreBStr = if (isSuspendB) "[SUSPEND]" else "[$finalB]"
            val resultSummary = "${sideA.name.uppercase()} $scoreAStr vs $scoreBStr ${sideB.name.uppercase()}"
            val winnerPart = if (winnerName != "DRAW") "\nWinner: ${winnerName.uppercase()}" else "\nRESULT: DRAW"
            
            // Battle Room Messages (Simplified)
            webSocketRepository.sendMessage(room, "/me [RESULT]\n$resultSummary$winnerPart\nCongrats!! Please leave the room", "REFEREE")
            
            // Auto-unlock & unmod Team Names (Captains)
            delay(1000)
            webSocketRepository.sendMessage(room, "/unlock", "REFEREE")
            webSocketRepository.sendMessage(room, "/unmod ${sideA.name}", "REFEREE")
            webSocketRepository.sendMessage(room, "/unmod ${sideB.name}", "REFEREE")
            
            // Send full result to VM
            onMatchFinished(MatchManager.MatchResult(room, currentPhase, winnerName, resultSummary, isSuspendA || isSuspendB, _kickLogs.value))
        }
    }

    fun stop() { voteTimer?.cancel(); postKickTimer?.cancel(); lockJob?.cancel(); _state.value = MatchState.Idle }
}

@Singleton
class MatchManager @Inject constructor(private val webSocketRepository: WebSocketRepository) {
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
    var mainBroadcastRoom: String = ""
    
    data class MatchResult(val room: String, val phase: String, val winner: String, val summary: String, val isSuspend: Boolean, val logs: List<KickLog>)
    var onMatchFinished: ((MatchResult) -> Unit)? = null

    init {
        scope.launch {
            webSocketRepository.subscribeEvents().collect { jsonObject ->
                val type = jsonObject["type"]?.jsonPrimitive?.content
                val room = jsonObject["room"]?.jsonPrimitive?.content?.lowercase() ?: ""
                when (type) {
                    "room.message.received", "room.message" -> {
                        val body = jsonObject["body"]?.jsonPrimitive?.content ?: ""
                        val serverMessageId = jsonObject["server_message_id"]?.jsonPrimitive?.content
                        sessions[room]?.handleMessage(body, serverMessageId)
                    }
                    "room.left" -> {
                        val username = jsonObject["username"]?.jsonPrimitive?.content ?: ""
                        sessions[room]?.handleUserLeft(username)
                    }
                }
            }
        }
    }

    fun startMatch(room: String, phase: String, nameA: String, teamAIds: List<String>, nameB: String, teamBIds: List<String>) {
        val normalizedRoom = room.lowercase()
        stopMatch(normalizedRoom)
        val session = MatchSession(normalizedRoom, webSocketRepository) { result ->
            broadcastResult(result)
            onMatchFinished?.invoke(result)
            stopMatch(result.room)
        }
        sessions[normalizedRoom] = session
        session.start(phase, nameA, teamAIds, nameB, teamBIds)
        updateActiveSessions()
    }

    fun broadcastResult(result: MatchResult) {
        if (mainBroadcastRoom.isNotEmpty()) {
            val winnerPart = if (result.winner != "DRAW") " Winner: ${result.winner.uppercase()}" else " RESULT: DRAW"
            webSocketRepository.sendMessage(mainBroadcastRoom, "/me [${result.room.uppercase()}]$winnerPart\n${result.summary}", "REFEREE")
        }
    }

    fun getSession(room: String): MatchSession? = sessions[room.lowercase()]
    private fun updateActiveSessions() { _activeSessions.value = sessions.keys().toList() }
    fun stopMatch(room: String) { val normalizedRoom = room.lowercase(); sessions[normalizedRoom]?.stop(); sessions.remove(normalizedRoom); updateActiveSessions() }
}
