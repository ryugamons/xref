package id.xterm.xref.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import id.xterm.xref.data.repository.WebSocketRepository

class HomeViewModel : ViewModel() {
    private val webSocketRepository = WebSocketRepository.getInstance()

    var refereeId by mutableStateOf("")
    var password by mutableStateOf("")
    var roomId by mutableStateOf("")
    
    val participants = mutableStateListOf(
        "TEAM ALPHA", "TEAM BETA", 
        "TEAM GAMMA", "TEAM DELTA",
        "TEAM EPSILON", "TEAM ZETA", 
        "TEAM ETA", "TEAM THETA"
    )

    val connectionState = webSocketRepository.connectionState

    fun login() {
        webSocketRepository.connect(refereeId, password)
    }

    fun logout() {
        webSocketRepository.disconnect()
    }

    fun joinRoom() {
        if (roomId.isNotEmpty()) {
            webSocketRepository.joinRoom(roomId)
        }
    }
    
    fun updateParticipant(index: Int, name: String) {
        if (index in participants.indices) {
            participants[index] = name
        }
    }
}
