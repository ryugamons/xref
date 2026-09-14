package id.xterm.xref.ui.arena

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import id.xterm.xref.core.match.MatchManager
import id.xterm.xref.data.repository.WebSocketRepository

class ArenaViewModel : ViewModel() {
    private val webSocketRepository = WebSocketRepository.getInstance()
    val matchManager = MatchManager.getInstance(webSocketRepository)
    
    var selectedRoom by mutableStateOf<String?>(null)

    var matchCallTemplate by mutableStateOf("[BROADCAST] Match starting: {teamA} vs {teamB}. Enter room: {room}")
    var bringMultiIdsTemplate by mutableStateOf("[REFEREE] Bring your 10 Multi-IDs into the room now!")
    var readyCheckTemplate by mutableStateOf("[REFEREE] Are you ready? Reply 'rd' to confirm!")
    var kickoffWarningTemplate by mutableStateOf("[REFEREE] KICKOFF STARTED! Do not vote/kick for 60 seconds!")

    fun startMatch(room: String, teamA: List<String>, teamB: List<String>) {
        matchManager.startMatch(room, teamA, teamB)
        selectedRoom = room
    }

    fun startTestMatch() {
        val teamA = (1..10).map { "A_ID_$it" }
        val teamB = (1..10).map { "B_ID_$it" }
        startMatch("war 1", teamA, teamB)
    }

    fun sendTemplate(templateText: String) {
        val room = selectedRoom ?: return
        val session = matchManager.getSession(room)
        val teamAName = session?.teamA?.value?.name ?: "Team A"
        val teamBName = session?.teamB?.value?.name ?: "Team B"
        
        val processedMessage = templateText
            .replace("{room}", room.uppercase())
            .replace("{teamA}", teamAName)
            .replace("{teamB}", teamBName)
            
        webSocketRepository.sendMessage(room, processedMessage, "REFEREE")
    }

    fun saveTemplates(matchCall: String, bringMultiIds: String, readyCheck: String, kickoffWarning: String) {
        matchCallTemplate = matchCall
        bringMultiIdsTemplate = bringMultiIds
        readyCheckTemplate = readyCheck
        kickoffWarningTemplate = kickoffWarning
    }
}
