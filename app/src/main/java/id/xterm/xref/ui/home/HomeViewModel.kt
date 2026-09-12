package id.xterm.xref.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.xterm.xref.data.repository.ConnectionState
import id.xterm.xref.data.repository.WebSocketRepository
import id.xterm.xref.data.storage.AuthPreferences
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
    var isBroadcastConnected by mutableStateOf(false)
    var isRefereeConnecting by mutableStateOf(false)
    var isBroadcastConnecting by mutableStateOf(false)
    
    var refereeStatusText by mutableStateOf("offline")
    var broadcastStatusText by mutableStateOf("offline")

    private var currentConnectionType: String? = null

    init {
        // Load saved credentials
        viewModelScope.launch {
            refereeId = AuthPreferences.getRefereeId()
            password = AuthPreferences.getRefereePassword()
            broadcastId = AuthPreferences.getBroadcastId()
            broadcastPassword = AuthPreferences.getBroadcastPassword()
        }

        viewModelScope.launch {
            webSocketRepository.connectionState.collect { state ->
                // Update text statuses
                when (state) {
                    is ConnectionState.Connected -> {
                        if (currentConnectionType == "REFEREE") refereeStatusText = "idle"
                        if (currentConnectionType == "BROADCAST") broadcastStatusText = "idle"
                    }
                    is ConnectionState.Connecting -> {
                        if (currentConnectionType == "REFEREE") refereeStatusText = "connecting"
                        if (currentConnectionType == "BROADCAST") broadcastStatusText = "connecting"
                    }
                    is ConnectionState.Disconnected, ConnectionState.Idle -> {
                        if (currentConnectionType == "REFEREE") refereeStatusText = "offline"
                        if (currentConnectionType == "BROADCAST") broadcastStatusText = "offline"
                    }
                    is ConnectionState.Error -> {
                        if (currentConnectionType == "REFEREE") refereeStatusText = "login failed"
                        if (currentConnectionType == "BROADCAST") broadcastStatusText = "login failed"
                    }
                }

                if (currentConnectionType == "REFEREE") {
                    isRefereeConnected = state is ConnectionState.Connected
                    isRefereeConnecting = state is ConnectionState.Connecting
                } else if (currentConnectionType == "BROADCAST") {
                    isBroadcastConnected = state is ConnectionState.Connected
                    isBroadcastConnecting = state is ConnectionState.Connecting
                }
                
                // Handle Disconnected or Error globally to clear connecting states
                if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                    if (currentConnectionType == "REFEREE") {
                        isRefereeConnected = false
                        isRefereeConnecting = false
                    } else if (currentConnectionType == "BROADCAST") {
                        isBroadcastConnected = false
                        isBroadcastConnecting = false
                    }
                }
            }
        }

        viewModelScope.launch {
            webSocketRepository.walletBalance.collect { balance ->
                if (currentConnectionType == "REFEREE") {
                    refereeCredits = balance
                } else if (currentConnectionType == "BROADCAST") {
                    broadcastCredits = balance
                }
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
        viewModelScope.launch {
            AuthPreferences.saveRefereeAuth(refereeId, password)
        }
        webSocketRepository.connect(refereeId, password)
    }

    fun disconnectReferee() {
        webSocketRepository.disconnect()
        currentConnectionType = null
        isRefereeConnected = false
        isRefereeConnecting = false
        refereeStatusText = "offline"
    }

    fun connectBroadcast() {
        currentConnectionType = "BROADCAST"
        viewModelScope.launch {
            AuthPreferences.saveBroadcastAuth(broadcastId, broadcastPassword)
        }
        webSocketRepository.connect(broadcastId, broadcastPassword)
    }

    fun disconnectBroadcast() {
        webSocketRepository.disconnect()
        currentConnectionType = null
        isBroadcastConnected = false
        isBroadcastConnecting = false
        broadcastStatusText = "offline"
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
