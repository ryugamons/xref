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

    init {
        // Load saved credentials
        viewModelScope.launch {
            refereeId = AuthPreferences.getRefereeId()
            password = AuthPreferences.getRefereePassword()
            broadcastId = AuthPreferences.getBroadcastId()
            broadcastPassword = AuthPreferences.getBroadcastPassword()
        }

        // Listen to Referee connection states
        viewModelScope.launch {
            webSocketRepository.refereeConnectionState.collect { state ->
                isRefereeConnected = state is ConnectionState.Connected
                isRefereeConnecting = state is ConnectionState.Connecting
                
                when (state) {
                    is ConnectionState.Connected -> refereeStatusText = "idle"
                    is ConnectionState.Connecting -> refereeStatusText = "connecting"
                    is ConnectionState.Disconnected, ConnectionState.Idle -> refereeStatusText = "offline"
                    is ConnectionState.Error -> refereeStatusText = "login failed"
                }
            }
        }

        // Listen to Broadcast connection states
        viewModelScope.launch {
            webSocketRepository.broadcastConnectionState.collect { state ->
                isBroadcastConnected = state is ConnectionState.Connected
                isBroadcastConnecting = state is ConnectionState.Connecting
                
                when (state) {
                    is ConnectionState.Connected -> broadcastStatusText = "idle"
                    is ConnectionState.Connecting -> broadcastStatusText = "connecting"
                    is ConnectionState.Disconnected, ConnectionState.Idle -> broadcastStatusText = "offline"
                    is ConnectionState.Error -> broadcastStatusText = "login failed"
                }
            }
        }

        // Listen to Referee wallet updates
        viewModelScope.launch {
            webSocketRepository.refereeWalletBalance.collect { balance ->
                refereeCredits = balance
            }
        }

        // Listen to Broadcast wallet updates
        viewModelScope.launch {
            webSocketRepository.broadcastWalletBalance.collect { balance ->
                broadcastCredits = balance
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

    fun connectReferee() {
        viewModelScope.launch {
            AuthPreferences.saveRefereeAuth(refereeId, password)
        }
        webSocketRepository.connect(refereeId, password, "REFEREE")
    }

    fun disconnectReferee() {
        webSocketRepository.disconnectSession("REFEREE")
    }

    fun connectBroadcast() {
        viewModelScope.launch {
            AuthPreferences.saveBroadcastAuth(broadcastId, broadcastPassword)
        }
        webSocketRepository.connect(broadcastId, broadcastPassword, "BROADCAST")
    }

    fun disconnectBroadcast() {
        webSocketRepository.disconnectSession("BROADCAST")
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
