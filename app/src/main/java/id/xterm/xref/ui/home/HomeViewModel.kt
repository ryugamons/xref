package id.xterm.xref.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.xterm.xref.data.repository.ConnectionState
import id.xterm.xref.data.repository.WebSocketRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val webSocketRepository = WebSocketRepository.getInstance()

    var refereeId by mutableStateOf("")
    var password by mutableStateOf("")
    var broadcastId by mutableStateOf("")
    var broadcastPassword by mutableStateOf("")
    var roomId by mutableStateOf("")

    var refereeCredits by mutableStateOf("0.00 CR")
    var broadcastCredits by mutableStateOf("0.00 CR")

    var isRefereeConnected by mutableStateOf(false)
        private set
    var isBroadcastConnected by mutableStateOf(false)
        private set
    var isRefereeConnecting by mutableStateOf(false)
        private set
    var isBroadcastConnecting by mutableStateOf(false)
        private set

    private var currentConnectionType: String? = null

    init {
        viewModelScope.launch {
            webSocketRepository.connectionState.collect { state ->
                isRefereeConnected = currentConnectionType == "REFEREE" && state is ConnectionState.Connected
                isBroadcastConnected = currentConnectionType == "BROADCAST" && state is ConnectionState.Connected
                isRefereeConnecting = currentConnectionType == "REFEREE" && state is ConnectionState.Connecting
                isBroadcastConnecting = currentConnectionType == "BROADCAST" && state is ConnectionState.Connecting
            }
        }
    }
    
    val participants = mutableStateListOf(
        "TEAM ALPHA", "TEAM BETA", 
        "TEAM GAMMA", "TEAM DELTA",
        "TEAM EPSILON", "TEAM ZETA", 
        "TEAM ETA", "TEAM THETA"
    )

    var bracketSize by mutableStateOf(8)
        private set

    val connectionState = webSocketRepository.connectionState

    fun login() {
        webSocketRepository.connect(refereeId, password)
    }

    fun logout() {
        webSocketRepository.disconnect()
    }

    fun connectReferee() {
        currentConnectionType = "REFEREE"
        webSocketRepository.connect(refereeId, password)
    }

    fun disconnectReferee() {
        if (currentConnectionType == "REFEREE") {
            webSocketRepository.disconnect()
            currentConnectionType = null
            isRefereeConnected = false
            isRefereeConnecting = false
        }
    }

    fun connectBroadcast() {
        currentConnectionType = "BROADCAST"
        webSocketRepository.connect(broadcastId, broadcastPassword)
    }

    fun disconnectBroadcast() {
        if (currentConnectionType == "BROADCAST") {
            webSocketRepository.disconnect()
            currentConnectionType = null
            isBroadcastConnected = false
            isBroadcastConnecting = false
        }
    }

    fun joinRoom() {
        if (roomId.isNotEmpty()) {
            webSocketRepository.joinRoom(roomId)
        }
    }
    
    fun changeBracketSize(size: Int) {
        if (size == 8 || size == 16) {
            bracketSize = size
            adjustParticipantsSize(size)
        }
    }

    private fun adjustParticipantsSize(targetSize: Int) {
        while (participants.size < targetSize) {
            participants.add("TEAM ${participants.size + 1}")
        }
        while (participants.size > targetSize) {
            participants.removeAt(participants.size - 1)
        }
    }
    
    fun updateParticipant(index: Int, name: String) {
        if (index in participants.indices) {
            participants[index] = name
        }
    }
}
