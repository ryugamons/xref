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
    private val onMatchFinished: (String, String, String, String, Boolean) -> Unit // room, phase, winnerName, scoreSummary, isSuspend
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
    }

    private fun parseServerTimeMs(serverMessageId: String?): Long {
        if (serverMessageId == null || !serverMessageId.startsWith("msg_")) return System.currentTimeMillis()
        return try {
            val raw = serverMessageId.substring(4) // Skip "msg_"
            val parts = raw.split(".")
            val tsPart = parts[0] // YYYYMMDDHHMMSS
            val nanoPart = if (parts.size > 1) parts[1] else "0"

            val year = tsPart.substring(0, 4).toInt()
            val month = tsPart.substring(4, 6).toInt()
            val day = tsPart.substring(6, 8).toInt()
            val hour = tsPart.substring(8, 10).toInt()
            val min = tsPart.substring(10, 12).toInt()
            val sec = tsPart.substring(12, 14).toInt()
            val ms = nanoPart.take(3).padEnd(3, '0').toInt()

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.set(year, month - 1, day, hour, min, sec)
            calendar.set(Calendar.MILLISECOND, ms)
            calendar.timeInMillis
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
            
            // Auto-lock room after 30s of battle
            lockJob?.cancel()
            lockJob = scope.launch {
                delay(30000)
                if (_state.value == MatchState.Battle) {
                    webSocketRepository.sendMessage(room, "/lock", "REFEREE")
                }
            }
            return
        }

        // 2. Goal confirmation
        if (body.contains("GOALLLLLLLLLL", ignoreCase = true)) {
            if (_state.value == MatchState.Battle || _state.value == MatchState.Ending) {
                // Snapshot the OFFICIAL score exactly at the moment of GOAL
                scoreSnapshotA = _teamA.value.participants.count { !it.isKicked }
                scoreSnapshotB = _teamB.value.participants.count { !it.isKicked }
                
                _state.value = MatchState.Ended
                calculateAndBroadcastResults()
            }
            return
        }

        // 3. Battle Logic (Keep logging kicks even during Ending/Ended phase for evidence until broadcast)
        if (_state.value == MatchState.Idle || _state.value == MatchState.Kickoff) return

        // Detect Vote
        if (body.contains("A vote to kick", ignoreCase = true) && body.contains("started by", ignoreCase = true)) {
            postKickTimer?.cancel()
            
            voteTimer?.cancel()
            voteTimer = scope.launch {
                delay(3000)
                if (_state.value == MatchState.Battle) {
                    sendGoal("TIMEOUT (VOTE HANG)")
                }
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
                if (_state.value == MatchState.Battle) {
                    sendGoal("TIMEOUT (NO RESPONSE)")
                }
            }

            val diff = if (battleStartTime > 0) serverNow - battleStartTime else 0L
            val team = getTeam(kickedUser)
            if (team != "Unknown") {
                val log = KickLog(team, kickedUser, diff, "KICKED")
                _kickLogs.update { (listOf(log) + it).take(100) }
                Log.d("XREF_MATCH", "Kick logged: $kickedUser at ${diff}ms (ServerTime)")
            }
        }

        // Vote Failed (During Battle)
        if (body.contains("Failed to kick", ignoreCase = true)) {
            val targetUser = body.replace("Failed to kick ", "", ignoreCase = true).trim().lowercase()
            voteTimer?.cancel()
            
            val diff = if (battleStartTime > 0) serverNow - battleStartTime else 0L
            val team = getTeam(targetUser)
            if (team != "Unknown") {
                val log = KickLog(team, targetUser, diff, "FAILED")
                _kickLogs.update { (listOf(log) + it).take(100) }
                Log.d("XREF_MATCH", "Vote failed logged: $targetUser at ${diff}ms (ServerTime)")
            }
        }
    }

    fun handleUserLeft(username: String) {
        if (_state.value != MatchState.Battle) return
        
        val user = username.lowercase()
        val team = getTeam(user)
        
        if (team != "Unknown") {
            // Explicit SUSPEND detection
            Log.d("XREF_MATCH", "Suspend detected: $username left room")
            sendGoal("SUSPEND ($team)")
        }
    }

    private fun markKicked(username: String) {
        val user = username.lowercase()
        _teamA.update { side ->
            side.copy(participants = side.participants.map { 
                if (it.id.lowercase() == user) it.copy(isKicked = true) else it 
            })
        }
        _teamB.update { side ->
            side.copy(participants = side.participants.map { 
                if (it.id.lowercase() == user) it.copy(isKicked = true) else it 
            })
        }
    }

    private fun getTeam(username: String): String {
        val user = username.lowercase()
        if (_teamA.value.participants.any { it.id.lowercase() == user }) return "A"
        if (_teamB.value.participants.any { it.id.lowercase() == user }) return "B"
        return "Unknown"
    }

    private fun sendGoal(reason: String) {
        if (isGoalSent) return
        isGoalSent = true
        endReason = reason
        _state.value = MatchState.Ending
        
        webSocketRepository.sendDirect(room, "/goal")
    }

    private fun calculateAndBroadcastResults() {
        scope.launch {
            delay(3000) 
            
            val sideA = _teamA.value
            val sideB = _teamB.value
            
            val isSuspendA = endReason.contains("SUSPEND (A)")
            val isSuspendB = endReason.contains("SUSPEND (B)")
            
            // Use snapshot for the official final scores (frozen at the moment of GOAL)
            val finalA = if (isSuspendA) 0 else scoreSnapshotA
            val finalB = if (isSuspendB) 0 else scoreSnapshotB
            
            val winnerName = when {
                isSuspendA -> sideB.name
                isSuspendB -> sideA.name
                finalA > finalB -> sideA.name
                finalB > finalA -> sideB.name
                else -> "DRAW"
            }
            
            val scoreAStr = if (isSuspendA) "[SUSPEND]" else "[$finalA]"
            val scoreBStr = if (isSuspendB) "[SUSPEND]" else "[$finalB]"
            
            val resultSummary = "${sideA.name.uppercase()} $scoreAStr vs $scoreBStr ${sideB.name.uppercase()}"
            val winnerPart = if (winnerName != "DRAW") "\nWinner: ${winnerName.uppercase()}" else "\nRESULT: DRAW"
            
            // Battle Room Message
            val resultMsg = "/me [RESULT]\n$resultSummary$winnerPart\nCongrats!! Please leave the room"
            webSocketRepository.sendMessage(room, resultMsg, "REFEREE")
            
            // Auto-unlock and unmod room 1s after results
            delay(1000)
            webSocketRepository.sendMessage(room, "/unlock", "REFEREE")
            webSocketRepository.sendMessage(room, "/unmod ${sideA.name}", "REFEREE")
            webSocketRepository.sendMessage(room, "/unmod ${sideB.name}", "REFEREE")
            
            // Notification for main broadcast with new format components
            onMatchFinished(room, currentPhase, winnerName, resultSummary, isSuspendA || isSuspendB)
        }
    }

    fun stop() {
        voteTimer?.cancel()
        postKickTimer?.cancel()
        lockJob?.cancel()
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
    
    var mainBroadcastRoom: String = ""
    
    data class MatchResult(
        val room: String, 
        val phase: String, 
        val winner: String, 
        val summary: String, 
        val isSuspend: Boolean
    )
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
        val session = sessions.getOrPut(normalizedRoom) { 
            MatchSession(normalizedRoom, webSocketRepository) { r, ph, winner, summary, susp ->
                val result = MatchResult(r, ph, winner, summary, susp)
                onMatchFinished?.invoke(result)
                stopMatch(r)
            }
        }
        session.start(phase, nameA, teamAIds, nameB, teamBIds)
        updateActiveSessions()
    }

    fun broadcastResult(result: MatchResult) {
        if (mainBroadcastRoom.isNotEmpty()) {
            val winnerPart = if (result.winner != "DRAW") " Winner: ${result.winner.uppercase()}" else " RESULT: DRAW"
            val msg = "/me [${result.room.uppercase()}]$winnerPart\n${result.summary}"
            webSocketRepository.sendMessage(mainBroadcastRoom, msg, "REFEREE")
        }
    }

    fun getSession(room: String): MatchSession? {
        return sessions[room.lowercase()]
    }

    private fun updateActiveSessions() {
        _activeSessions.value = sessions.keys().toList().toMutableList()
    }

    fun stopMatch(room: String) {
        val normalizedRoom = room.lowercase()
        sessions[normalizedRoom]?.stop()
        sessions.remove(normalizedRoom)
        updateActiveSessions()
    }
}
