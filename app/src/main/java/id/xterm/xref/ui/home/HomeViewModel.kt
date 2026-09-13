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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class MatchPhase {
    IDLE, REGISTRATION, ROLLING, COMPLETED
}

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
    var isRegistrationFeeEnabled by mutableStateOf(false)
    var registrationFeeNominal by mutableStateOf("0")
    
    // System Settings
    var walletPin by mutableStateOf("123456")
    var broadcastIntervalSeconds by mutableStateOf(60)

    // Match Active State
    var matchPhase by mutableStateOf(MatchPhase.IDLE)
    val registeredParticipants = mutableStateListOf<String>()
    val participantRolls = mutableStateMapOf<String, Int>()
    
    private var broadcastJob: Job? = null
    private var historyCheckJob: Job? = null

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
        // Load saved credentials, rooms, match settings and system settings
        viewModelScope.launch {
            refereeId = AuthPreferences.getRefereeId()
            refereePassword = AuthPreferences.getRefereePassword()
            starterId = AuthPreferences.getStarterId()
            starterPassword = AuthPreferences.getStarterPassword()
            
            broadcastRoom = AuthPreferences.getBroadcastRoom()
            val savedBattleRooms = AuthPreferences.getBattleRooms()
            if (savedBattleRooms.isEmpty()) {
                battleRooms.add("") 
            } else {
                battleRooms.addAll(savedBattleRooms)
            }

            bracketSize = AuthPreferences.getMatchTeamCount()
            isRegistrationFeeEnabled = AuthPreferences.getMatchFeeEnabled()
            registrationFeeNominal = AuthPreferences.getMatchFeeNominal()
            
            walletPin = AuthPreferences.getWalletPin()
            broadcastIntervalSeconds = AuthPreferences.getBroadcastInterval()
        }

        // Listen to Referee connection states
        viewModelScope.launch {
            webSocketRepository.refereeConnectionState.collect { state ->
                isRefereeConnected = state is ConnectionState.Connected
                isRefereeConnecting = state is ConnectionState.Connecting
                
                when (state) {
                    is ConnectionState.Connected -> refereeStatusText = "idle"
                    is ConnectionState.Connecting -> refereeStatusText = "connecting"
                    is ConnectionState.Disconnected, ConnectionState.Idle -> {
                        refereeStatusText = "offline"
                        if (matchPhase != MatchPhase.IDLE) resetMatch()
                    }
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

        // Listen to wallet updates for all users
        viewModelScope.launch {
            webSocketRepository.events.collect { json ->
                if (json["type"]?.jsonPrimitive?.content == "wallet.updated" && matchPhase == MatchPhase.REGISTRATION) {
                    val usernameInPacket = json["username"]?.jsonPrimitive?.content
                    
                    // If our own balance updated, it might be a transfer
                    if (usernameInPacket == refereeId) {
                        checkWalletHistoryThrottled()
                    }
                    
                    // Registration could be free
                    if (!isRegistrationFeeEnabled && usernameInPacket != null && 
                        usernameInPacket != refereeId && usernameInPacket != starterId) {
                        if (!registeredParticipants.contains(usernameInPacket) && registeredParticipants.size < bracketSize) {
                            registeredParticipants.add(usernameInPacket)
                            sendRegistrationProgress(usernameInPacket, 0L, 0L)
                            checkRegistrationFull()
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
            webSocketRepository.roomMessages.collect { pair ->
                val roomName = pair.first
                val message = pair.second
                
                // 1. Detect transfer message from system
                if (matchPhase == MatchPhase.REGISTRATION && message.username == "system") {
                    val regex = "([a-zA-Z0-9_]+) transferred ([0-9]+) credits".toRegex(RegexOption.IGNORE_CASE)
                    val match = regex.find(message.text)
                    if (match != null) {
                        val sender = match.groupValues[1]
                        val amountCr = match.groupValues[2].toLongOrNull() ?: 0L
                        processTransfer(sender, amountCr * 1000)
                    }
                }

                // 2. Detect Roll results: ** hex rolls 6 **
                if (matchPhase == MatchPhase.ROLLING) {
                    val rollRegex = "\\*\\*\\s+([a-zA-Z0-9_]+)\\s+rolls\\s+([0-9]+)\\s+\\*\\*".toRegex(RegexOption.IGNORE_CASE)
                    val rollMatch = rollRegex.find(message.text)
                    if (rollMatch != null) {
                        val roller = rollMatch.groupValues[1]
                        val value = rollMatch.groupValues[2].toIntOrNull() ?: 0
                        
                        if (registeredParticipants.contains(roller) && !participantRolls.containsKey(roller)) {
                            participantRolls[roller] = value
                            checkAllRollsCompleted()
                        }
                    }
                }

                val list = _roomMessagesMap.getOrPut(roomName) { mutableStateListOf() }
                list.add(message)
                if (list.size > 100) list.removeAt(0)
            }
        }
    }

    private fun checkWalletHistoryThrottled() {
        historyCheckJob?.cancel()
        historyCheckJob = viewModelScope.launch {
            delay(500) 
            val history = webSocketRepository.getWalletHistory("REFEREE")
            val lastTransfer = history?.transactions?.firstOrNull { it.type == "transfer_in" }
            
            if (lastTransfer != null) {
                val sender = lastTransfer.note
                    .replace("Credit transfer from ", "", ignoreCase = true)
                    .trim()
                
                if (sender.isNotEmpty()) {
                    processTransfer(sender, lastTransfer.amountMilliCr)
                }
            }
        }
    }

    fun toggleMatchRegistration() {
        if (matchPhase == MatchPhase.IDLE) {
            if (!isRefereeConnected) return
            matchPhase = MatchPhase.REGISTRATION
            startBroadcastingStatus()
        } else {
            resetMatch()
        }
    }

    private fun resetMatch() {
        matchPhase = MatchPhase.IDLE
        stopBroadcasting()
        registeredParticipants.clear()
        participantRolls.clear()
    }

    private fun checkRegistrationFull() {
        if (registeredParticipants.size >= bracketSize) {
            matchPhase = MatchPhase.ROLLING
            // Final registration message
            sendMatchClosedMessage()
            // Switch to instruction for Rolling
            startBroadcastingStatus()
        }
    }

    fun startRollPhaseManually() {
        if (matchPhase == MatchPhase.REGISTRATION) {
            matchPhase = MatchPhase.ROLLING
            sendMatchClosedMessage()
            startBroadcastingStatus()
        }
    }

    private fun startBroadcastingStatus() {
        broadcastJob?.cancel()
        broadcastJob = viewModelScope.launch {
            while (isActive) {
                when (matchPhase) {
                    MatchPhase.REGISTRATION -> sendCurrentMatchStatus()
                    MatchPhase.ROLLING -> sendRollInstructions()
                    else -> {}
                }
                delay(broadcastIntervalSeconds * 1000L) 
            }
        }
    }

    private fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    fun sendCurrentMatchStatus() {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        val isJoinedInBroadcastRoom = activeRooms.value.contains(normalizedBroadcastRoom)

        if (isRefereeConnected && broadcastRoom.isNotEmpty() && isJoinedInBroadcastRoom) {
            val joinedText = if (registeredParticipants.isEmpty()) "" else registeredParticipants.joinToString(", ")
            val feeText = if (isRegistrationFeeEnabled) "$registrationFeeNominal CR" else "FREE"
            val message = "/me [XREF] OPEN MATCH $bracketSize USER - FEE $feeText - TRF ID $refereeId [$joinedText]"

            activeRooms.value.forEach { room ->
                webSocketRepository.sendMessage(room, message, "REFEREE")
            }
        }
    }

    private fun sendRollInstructions() {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        val isJoinedInBroadcastRoom = activeRooms.value.contains(normalizedBroadcastRoom)

        if (isRefereeConnected && broadcastRoom.isNotEmpty() && isJoinedInBroadcastRoom) {
            val pendingRolls = registeredParticipants.filter { !participantRolls.containsKey(it) }
            if (pendingRolls.isNotEmpty()) {
                val message = "/me [XREF] REGISTRATION FULL. ALL PARTICIPANTS PLEASE SEND /roll NOW! PENDING: [${pendingRolls.joinToString(", ")}]"
                activeRooms.value.forEach { room ->
                    webSocketRepository.sendMessage(room, message, "REFEREE")
                }
            }
        }
    }

    private fun checkAllRollsCompleted() {
        if (participantRolls.size >= registeredParticipants.size) {
            matchPhase = MatchPhase.COMPLETED
            stopBroadcasting()
            applyRollSeeding()
        }
    }

    private fun applyRollSeeding() {
        // Sorting: High vs Low
        // Get pairs of (username, roll)
        val sortedByRoll = participantRolls.toList().sortedByDescending { it.second }
        
        val newList = mutableListOf<String>()
        val n = sortedByRoll.size
        
        // High vs Low pairing: 1 vs N, 2 vs N-1, etc.
        for (i in 0 until n / 2) {
            newList.add(sortedByRoll[i].first)      // Highest
            newList.add(sortedByRoll[n - 1 - i].first) // Lowest
        }
        
        // Update the list that BracketScreen uses
        registeredParticipants.clear()
        registeredParticipants.addAll(newList)
        
        // Broadcast result
        broadcastSeedingResult()
    }

    private fun broadcastSeedingResult() {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && activeRooms.value.contains(normalizedBroadcastRoom)) {
            val message = "/me [XREF] ROLLING COMPLETED. BRACKET GENERATED (HIGH vs LOW). CHECK BRACKET TAB!"
            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
        }
    }

    fun processTransfer(sender: String, amountMilliCr: Long) {
        if (matchPhase != MatchPhase.REGISTRATION) return
        
        val requiredMilliCr = if (isRegistrationFeeEnabled) {
            registrationFeeNominal.toLongOrNull()?.let { it * 1000 } ?: 0L
        } else 0L
        
        if (amountMilliCr >= requiredMilliCr) {
            if (!registeredParticipants.contains(sender) && registeredParticipants.size < bracketSize) {
                registeredParticipants.add(sender)
                
                val refundMilliCr = if (requiredMilliCr > 0) amountMilliCr - requiredMilliCr else 0L
                if (refundMilliCr > 0) {
                    viewModelScope.launch {
                        webSocketRepository.sendTransfer(sender, refundMilliCr, walletPin, "REFEREE")
                    }
                }

                sendRegistrationProgress(sender, amountMilliCr / 1000, refundMilliCr / 1000)
                checkRegistrationFull()
            }
        }
    }

    private fun sendRegistrationProgress(username: String, amountCr: Long, refundCr: Long = 0L) {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        val isJoinedInBroadcastRoom = activeRooms.value.contains(normalizedBroadcastRoom)

        if (isRefereeConnected && broadcastRoom.isNotEmpty() && isJoinedInBroadcastRoom) {
            val count = registeredParticipants.size
            val total = bracketSize
            val refundText = if (refundCr > 0) " ${refundCr}CR REFUNDED." else ""
            
            val message = if (isRegistrationFeeEnabled) {
                "/me [XREF] ${username.uppercase()} TRANSFER ${amountCr}CR ✅.$refundText REGISTRATION $count/$total."
            } else {
                "/me [XREF] ${username.uppercase()} JOINED ✅. REGISTRATION $count/$total."
            }

            activeRooms.value.forEach { room ->
                webSocketRepository.sendMessage(room, message, "REFEREE")
            }
        }
    }

    private fun sendMatchClosedMessage() {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        val isJoinedInBroadcastRoom = activeRooms.value.contains(normalizedBroadcastRoom)

        if (isRefereeConnected && broadcastRoom.isNotEmpty() && isJoinedInBroadcastRoom) {
            val joinedText = registeredParticipants.joinToString(", ")
            val message = "/me [XREF] REGISTRATION CLOSED ($bracketSize/$bracketSize) ✅. PREPARING ROLLING PHASE..."

            activeRooms.value.forEach { room ->
                webSocketRepository.sendMessage(room, message, "REFEREE")
            }
        }
    }

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
        saveMatchPrefs()
    }

    private fun saveMatchPrefs() {
        viewModelScope.launch {
            AuthPreferences.saveMatchSettings(bracketSize, isRegistrationFeeEnabled, registrationFeeNominal)
        }
    }

    // System Settings
    fun updateWalletPin(pin: String) {
        walletPin = pin
        viewModelScope.launch { AuthPreferences.saveWalletPin(pin) }
    }

    fun updateBroadcastInterval(seconds: Int) {
        broadcastIntervalSeconds = seconds
        viewModelScope.launch { AuthPreferences.saveBroadcastInterval(seconds) }
        
        // Re-start broadcasting to apply the new interval immediately
        if (matchPhase != MatchPhase.IDLE) {
            startBroadcastingStatus()
        }
    }
}
