package id.xterm.xref.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.xterm.xref.core.websocket.ChatMessage
import id.xterm.xref.core.websocket.MessageType
import id.xterm.xref.data.repository.ConnectionState
import id.xterm.xref.data.repository.WebSocketRepository
import id.xterm.xref.data.storage.AuthPreferences
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val webSocketRepository = WebSocketRepository.getInstance()

    var refereeId by mutableStateOf("")
    var refereePassword by mutableStateOf("")
    var starterId by mutableStateOf("")
    var starterPassword by mutableStateOf("")
    
    // Room states
    var broadcastRoom by mutableStateOf("")
    val battleRooms = mutableStateListOf<String>()

    var refereeCredits by mutableStateOf("0.00 CR")
    var starterCredits by mutableStateOf("0.00 CR")

    var isRefereeConnected by mutableStateOf(false)
    var isStarterConnected by mutableStateOf(false)
    var isRefereeConnecting by mutableStateOf(false)
    var isStarterConnecting by mutableStateOf(false)
    
    var refereeStatusText by mutableStateOf("offline")
    var starterStatusText by mutableStateOf("offline")

    // Active rooms tracking
    val activeRooms = webSocketRepository.activeRooms
    private val _roomMessagesMap = mutableStateMapOf<String, SnapshotStateList<ChatMessage>>()
    val roomMessagesMap: Map<String, List<ChatMessage>> = _roomMessagesMap

    init {
        // Load saved credentials and rooms
        viewModelScope.launch {
            refereeId = AuthPreferences.getRefereeId()
            refereePassword = AuthPreferences.getRefereePassword()
            starterId = AuthPreferences.getStarterId()
            starterPassword = AuthPreferences.getStarterPassword()
            
            broadcastRoom = AuthPreferences.getBroadcastRoom()
            val savedBattleRooms = AuthPreferences.getBattleRooms()
            if (savedBattleRooms.isEmpty()) {
                battleRooms.add("") // Default one empty field
            } else {
                battleRooms.addAll(savedBattleRooms)
            }
        }

        // Listen to Referee (Broadcaster Role) connection states
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

        // Listen to Starter connection states
        viewModelScope.launch {
            webSocketRepository.starterConnectionState.collect { state ->
                isStarterConnected = state is ConnectionState.Connected
                isStarterConnecting = state is ConnectionState.Connecting
                
                when (state) {
                    is ConnectionState.Connected -> starterStatusText = "idle"
                    is ConnectionState.Connecting -> starterStatusText = "connecting"
                    is ConnectionState.Disconnected, ConnectionState.Idle -> starterStatusText = "offline"
                    is ConnectionState.Error -> starterStatusText = "login failed"
                }
            }
        }

        // Listen to Referee wallet updates
        viewModelScope.launch {
            webSocketRepository.refereeWalletBalance.collect { balance ->
                refereeCredits = balance
            }
        }

        // Listen to Starter wallet updates
        viewModelScope.launch {
            webSocketRepository.starterWalletBalance.collect { balance ->
                starterCredits = balance
            }
        }

        // Listen to room messages
        viewModelScope.launch {
            webSocketRepository.roomMessages.collect { (roomName, message) ->
                val list = _roomMessagesMap.getOrPut(roomName) { mutableStateListOf() }
                list.add(message)
                // Keep only last 100 messages per room
                if (list.size > 100) {
                    list.removeAt(0)
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

    fun connectReferee() {
        viewModelScope.launch {
            AuthPreferences.saveRefereeAuth(refereeId, refereePassword)
        }
        webSocketRepository.connect(refereeId, refereePassword, "REFEREE")
    }

    fun disconnectReferee() {
        webSocketRepository.disconnectSession("REFEREE")
    }

    fun connectStarter() {
        viewModelScope.launch {
            AuthPreferences.saveStarterAuth(starterId, starterPassword)
        }
        webSocketRepository.connect(starterId, starterPassword, "STARTER")
    }

    fun disconnectStarter() {
        webSocketRepository.disconnectSession("STARTER")
    }

    // Room management
    fun updateBattleRoom(index: Int, name: String) {
        if (index in battleRooms.indices) {
            battleRooms[index] = name.lowercase()
            saveRoomPrefs()
        }
    }

    fun addBattleRoom() {
        battleRooms.add("")
        saveRoomPrefs()
    }

    fun removeBattleRoom(index: Int) {
        if (battleRooms.size > 1) {
            battleRooms.removeAt(index)
            saveRoomPrefs()
        }
    }

    fun updateBroadcastRoom(name: String) {
        broadcastRoom = name.lowercase()
        saveRoomPrefs()
    }

    private fun saveRoomPrefs() {
        viewModelScope.launch {
            AuthPreferences.saveRooms(broadcastRoom, battleRooms.toList())
        }
    }

    fun joinBroadcastRoom() {
        if (broadcastRoom.isNotEmpty()) {
            // Referee ID acts as Broadcaster (enters rooms)
            webSocketRepository.joinRoom(broadcastRoom, "REFEREE")
        }
    }

    fun leaveBroadcastRoom() {
        if (broadcastRoom.isNotEmpty()) {
            webSocketRepository.leaveRoom(broadcastRoom, "REFEREE")
        }
    }

    fun joinBattleRoom(index: Int) {
        val room = battleRooms.getOrNull(index)
        if (!room.isNullOrEmpty()) {
            // Referee ID acts as Broadcaster (enters rooms)
            webSocketRepository.joinRoom(room, "REFEREE")
        }
    }

    fun leaveBattleRoom(index: Int) {
        val room = battleRooms.getOrNull(index)
        if (!room.isNullOrEmpty()) {
            webSocketRepository.leaveRoom(room, "REFEREE")
        }
    }

    fun sendRoomMessage(room: String, message: String) {
        if (message.isNotEmpty()) {
            webSocketRepository.sendMessage(room, message, "REFEREE")
        }
    }

    fun joinRooms() {
        if (broadcastRoom.isNotEmpty()) {
            webSocketRepository.joinRoom(broadcastRoom, "REFEREE")
        }
        battleRooms.forEach { room ->
            if (room.isNotEmpty()) {
                webSocketRepository.joinRoom(room, "REFEREE")
            }
        }
    }

    fun leaveRooms() {
        if (broadcastRoom.isNotEmpty()) {
            webSocketRepository.leaveRoom(broadcastRoom, "REFEREE")
        }
        battleRooms.forEach { room ->
            if (room.isNotEmpty()) {
                webSocketRepository.leaveRoom(room, "REFEREE")
            }
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
