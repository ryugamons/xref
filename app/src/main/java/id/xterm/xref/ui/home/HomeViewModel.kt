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

    // Match configuration
    var bracketSize by mutableStateOf(8)
        private set
    var isRegistrationFeeEnabled by mutableStateOf(false)
    var registrationFeeNominal by mutableStateOf("0")

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

    // UI state persistence
    var selectedRoomInRoomsTab by mutableStateOf<String?>(null)

    init {
        // Load saved credentials, rooms, and match settings
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

            bracketSize = AuthPreferences.getMatchTeamCount()
            isRegistrationFeeEnabled = AuthPreferences.getMatchFeeEnabled()
            registrationFeeNominal = AuthPreferences.getMatchFeeNominal()
            
            // Sync participants size with loaded bracket size
            adjustParticipantsSize(bracketSize)
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
                    is ConnectionState.Error -> {
                        refereeStatusText = if (state.message.contains("Broken pipe", ignoreCase = true) || 
                            state.message.contains("closed", ignoreCase = true)) {
                            "connection lost"
                        } else {
                            "login failed"
                        }
                    }
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
                    is ConnectionState.Error -> {
                        starterStatusText = if (state.message.contains("Broken pipe", ignoreCase = true) || 
                            state.message.contains("closed", ignoreCase = true)) {
                            "connection lost"
                        } else {
                            "login failed"
                        }
                    }
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

    fun connectReferee() {
        viewModelScope.launch {
            AuthPreferences.saveRefereeAuth(refereeId, refereePassword)
            webSocketRepository.loginAndConnect(refereeId, refereePassword, "REFEREE")
        }
    }

    fun disconnectReferee() {
        webSocketRepository.disconnectSession("REFEREE")
    }

    fun connectStarter() {
        viewModelScope.launch {
            AuthPreferences.saveStarterAuth(starterId, starterPassword)
            webSocketRepository.loginAndConnect(starterId, starterPassword, "STARTER")
        }
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
            _roomMessagesMap.remove(broadcastRoom.lowercase())
            if (selectedRoomInRoomsTab == broadcastRoom.lowercase()) {
                selectedRoomInRoomsTab = null
            }
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
            _roomMessagesMap.remove(room.lowercase())
            if (selectedRoomInRoomsTab == room.lowercase()) {
                selectedRoomInRoomsTab = null
            }
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
            _roomMessagesMap.remove(broadcastRoom.lowercase())
        }
        battleRooms.forEach { room ->
            if (room.isNotEmpty()) {
                webSocketRepository.leaveRoom(room, "REFEREE")
                _roomMessagesMap.remove(room.lowercase())
            }
        }
        selectedRoomInRoomsTab = null
    }
    
    // Match Setup
    fun updateRegistrationFee(enabled: Boolean) {
        isRegistrationFeeEnabled = enabled
        saveMatchPrefs()
    }

    fun updateRegistrationFeeNominal(nominal: String) {
        registrationFeeNominal = nominal
        saveMatchPrefs()
    }

    fun changeBracketSize(size: Int) {
        bracketSize = size
        adjustParticipantsSize(size)
        saveMatchPrefs()
    }

    private fun saveMatchPrefs() {
        viewModelScope.launch {
            AuthPreferences.saveMatchSettings(bracketSize, isRegistrationFeeEnabled, registrationFeeNominal)
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
