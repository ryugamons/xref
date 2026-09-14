package id.xterm.xref.ui.home

import androidx.compose.runtime.derivedStateOf
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
    IDLE, REGISTRATION, ROLLING, BRACKET_READY, IN_PROGRESS, FINISHED
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
    var isRegistrationFree by mutableStateOf(false)
    var isRegistrationFeeEnabled by mutableStateOf(false)
    var registrationFeeNominal by mutableStateOf("0")
    
    // System Settings
    var walletPin by mutableStateOf("123456")
    var broadcastIntervalSeconds by mutableStateOf(60)
    var turneyTitle by mutableStateOf("XREF")
    var multiLoginTemplate by mutableStateOf("BRING YOUR 10 MULTI-IDS INTO ROOM {room} NOW!")

    // Match Active State
    var matchPhase by mutableStateOf(MatchPhase.IDLE)
    val registeredParticipants = mutableStateListOf<String>()
    val participantRolls = mutableStateMapOf<String, String>()
    val participantsWhoMustReRoll = mutableStateListOf<String>()
    
    val duplicateRolls by derivedStateOf {
        participantRolls.values
            .filter { it.toIntOrNull() != null }
            .groupBy { it }
            .filter { it.value.size > 1 }
            .keys
    }
    
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
            isRegistrationFree = !isRegistrationFeeEnabled
            registrationFeeNominal = AuthPreferences.getMatchFeeNominal()
            
            walletPin = AuthPreferences.getWalletPin()
            broadcastIntervalSeconds = AuthPreferences.getBroadcastInterval()
            turneyTitle = AuthPreferences.getTurneyTitle()
            multiLoginTemplate = AuthPreferences.getMultiLoginTemplate()
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
                        if (matchPhase != MatchPhase.IDLE && matchPhase != MatchPhase.FINISHED) {
                           // Stay in current phase even if disconnected temporarily
                        }
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
                    
                    if (usernameInPacket == refereeId) {
                        checkWalletHistoryThrottled()
                    }
                    
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
                
                if (matchPhase == MatchPhase.REGISTRATION && message.username == "system") {
                    val regex = "([a-zA-Z0-9_]+) transferred ([0-9]+) credits".toRegex(RegexOption.IGNORE_CASE)
                    val match = regex.find(message.text)
                    if (match != null) {
                        val sender = match.groupValues[1]
                        val amountCr = match.groupValues[2].toLongOrNull() ?: 0L
                        processTransfer(sender, amountCr * 1000)
                    }
                }

                if (matchPhase == MatchPhase.ROLLING) {
                    val rollRegex = "(?:\\*\\*\\s+)?([a-zA-Z0-9_]+)\\s+rolls\\s+([0-9]+)".toRegex(RegexOption.IGNORE_CASE)
                    val rollMatch = rollRegex.find(message.text)
                    if (rollMatch != null) {
                        val roller = rollMatch.groupValues[1]
                        val value = rollMatch.groupValues[2]
                        
                        if (registeredParticipants.contains(roller)) {
                            val hasExisting = participantRolls.containsKey(roller)
                            val mustReRoll = participantsWhoMustReRoll.contains(roller)
                            
                            if (!hasExisting || mustReRoll) {
                                val isDuplicateOfSomeoneElse = participantRolls.filter { it.key != roller }.values.contains(value)
                                
                                if (isDuplicateOfSomeoneElse) {
                                    val normalizedBroadcastRoom = broadcastRoom.lowercase()
                                    if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
                                        val duplicateMsg = "[SYSTEM] Duplicate roll: $value from $roller. Please re-roll!"
                                        webSocketRepository.sendMessage(normalizedBroadcastRoom, duplicateMsg, "REFEREE")
                                    }
                                    if (!participantsWhoMustReRoll.contains(roller)) {
                                        participantsWhoMustReRoll.add(roller)
                                    }
                                } else {
                                    participantsWhoMustReRoll.remove(roller)
                                }
                                
                                participantRolls[roller] = value
                                checkRollsComplete()
                            }
                        }
                    }
                }

                // 3. Detect "JOIN" command for FREE matches
                if (matchPhase == MatchPhase.REGISTRATION && !isRegistrationFeeEnabled) {
                    if (message.text.trim().equals("JOIN", ignoreCase = true)) {
                        val sender = message.username
                        if (sender != "system" && sender != refereeId && sender != starterId) {
                            if (!registeredParticipants.contains(sender) && registeredParticipants.size < bracketSize) {
                                registeredParticipants.add(sender)
                                sendRegistrationProgress(sender, 0L, 0L)
                                checkRegistrationFull()
                            }
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
        if (matchPhase == MatchPhase.IDLE || matchPhase == MatchPhase.FINISHED) {
            if (!isRefereeConnected) return
            // Start fresh registration or continue with existing participants
            matchPhase = MatchPhase.REGISTRATION
            startBroadcastingStatus()
        } else {
            // STOP/ABORT - We keep the participants and rolls!
            stopBroadcasting()
            matchPhase = MatchPhase.IDLE
        }
    }

    fun cancelMatchAndRefund() {
        viewModelScope.launch {
            val normalizedBroadcastRoom = broadcastRoom.lowercase()
            
            if (isRefereeConnected && broadcastRoom.isNotEmpty()) {
                val isJoined = activeRooms.value.contains(normalizedBroadcastRoom)
                if (isJoined) {
                    if (isRegistrationFree || !isRegistrationFeeEnabled) {
                        webSocketRepository.sendMessage(
                            normalizedBroadcastRoom,
                            "[SYSTEM] Match cancelled by Referee. Registration was free, no refunds required.",
                            "REFEREE"
                        )
                    } else {
                        val participantsToRefund = registeredParticipants.toList()
                        webSocketRepository.sendMessage(
                            normalizedBroadcastRoom,
                            "/me [SYSTEM] Match cancelled. Refunding credits to all registered participants.",
                            "REFEREE"
                        )
                        
                        if (isRegistrationFeeEnabled) {
                            val feeMilliCr = registrationFeeNominal.toLongOrNull()?.let { it * 1000 } ?: 0L
                            if (feeMilliCr > 0) {
                                participantsToRefund.forEach { participant ->
                                    // Send broadcast notification/command (respects 10ms queue)
                                    webSocketRepository.sendMessage(
                                        normalizedBroadcastRoom,
                                        "/me [SYSTEM] Refunding ${feeMilliCr / 1000}CR to ${participant.uppercase()}...",
                                        "REFEREE"
                                    )
                                    // Actual refund via API
                                    webSocketRepository.sendTransfer(participant, feeMilliCr, walletPin, "REFEREE")
                                    
                                    // Explicit throttling as requested
                                    delay(10)
                                }
                            }
                        }
                    }
                }
            }
            
            clearAllMatchData()
        }
    }

    fun clearAllMatchData() {
        stopBroadcasting()
        registeredParticipants.clear()
        participantRolls.clear()
        participantsWhoMustReRoll.clear()
        matchPhase = MatchPhase.IDLE
    }
    
    fun finishTournamentManually() {
        matchPhase = MatchPhase.FINISHED
    }

    fun addParticipant(name: String) {
        if (name.isNotBlank() && registeredParticipants.size < bracketSize && matchPhase == MatchPhase.REGISTRATION) {
            if (!registeredParticipants.contains(name)) {
                registeredParticipants.add(name)
                sendRegistrationProgress(name, 0L, 0L)
            }
        }
    }

    private fun checkRegistrationFull() {
        // Manual transition via "START ROLL" button
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
            val instruction = if (isRegistrationFeeEnabled) "TRF ID $refereeId" else "Type JOIN to enter!"
            
            val message = "/me [$turneyTitle] OPEN MATCH $bracketSize USER - FEE $feeText - $instruction [$joinedText]"

            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
        }
    }

    private fun sendRollInstructions() {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        val isJoinedInBroadcastRoom = activeRooms.value.contains(normalizedBroadcastRoom)

        if (isRefereeConnected && broadcastRoom.isNotEmpty() && isJoinedInBroadcastRoom) {
            val pendingRolls = registeredParticipants.filter { !participantRolls.containsKey(it) }
            if (pendingRolls.isNotEmpty()) {
                val message = "/me [$turneyTitle] REGISTRATION FULL. ALL PARTICIPANTS PLEASE SEND /roll NOW! PENDING: [${pendingRolls.joinToString(", ")}]"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
            }
        }
    }

    fun seedBracketManually() {
        if (matchPhase == MatchPhase.ROLLING && participantRolls.size >= registeredParticipants.size) {
            matchPhase = MatchPhase.BRACKET_READY
            stopBroadcasting()
            applyRollSeeding()
        }
    }

    private fun checkRollsComplete() {
        if (matchPhase == MatchPhase.ROLLING && 
            participantRolls.size >= registeredParticipants.size && 
            duplicateRolls.isEmpty()) {
            matchPhase = MatchPhase.BRACKET_READY
            stopBroadcasting()
            applyRollSeeding()
        }
    }

    fun updateParticipantRoll(name: String, rollText: String) {
        if (rollText.isEmpty()) {
            participantRolls.remove(name)
            participantsWhoMustReRoll.remove(name)
            checkRollsComplete()
            return
        }
        val rollInt = rollText.toIntOrNull() ?: return
        val valueStr = rollInt.toString()
        participantRolls[name] = valueStr

        val isDuplicateOfSomeoneElse = participantRolls.filter { it.key != name }.values.contains(valueStr)
        if (isDuplicateOfSomeoneElse) {
            val normalizedBroadcastRoom = broadcastRoom.lowercase()
            if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
                val duplicateMsg = "[SYSTEM] Duplicate roll: $valueStr from $name. Please re-roll!"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, duplicateMsg, "REFEREE")
            }
            if (!participantsWhoMustReRoll.contains(name)) {
                participantsWhoMustReRoll.add(name)
            }
        } else {
            participantsWhoMustReRoll.remove(name)
        }
        checkRollsComplete()
    }

    private fun applyRollSeeding() {
        val sortedByRoll = participantRolls.toList().sortedByDescending { it.second.toIntOrNull() ?: 0 }
        val newList = mutableListOf<String>()
        val n = sortedByRoll.size
        
        for (i in 0 until n / 2) {
            newList.add(sortedByRoll[i].first)      
            newList.add(sortedByRoll[n - 1 - i].first) 
        }
        
        registeredParticipants.clear()
        registeredParticipants.addAll(newList)
        broadcastSeedingResult()
    }

    private fun broadcastSeedingResult() {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && activeRooms.value.contains(normalizedBroadcastRoom)) {
            val pairs = mutableListOf<String>()
            for (i in 0 until registeredParticipants.size step 2) {
                if (i + 1 < registeredParticipants.size) {
                    pairs.add("${registeredParticipants[i].uppercase()} vs ${registeredParticipants[i+1].uppercase()}")
                }
            }
            val seedingText = pairs.joinToString(" | ")
                val message = "/me [$turneyTitle] ROLLING COMPLETED. BRACKET GENERATED: $seedingText"
            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
        }
    }
    
    fun callMatchSummon(teamA: String, teamB: String): String {
        matchPhase = MatchPhase.IN_PROGRESS
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        
        // Find a room to use: first joined battle room, OR first battle room, OR "arena"
        val roomToUse = battleRooms.firstOrNull { 
            it.isNotEmpty() && activeRooms.value.contains(it.lowercase()) && it.lowercase() != normalizedBroadcastRoom 
        } ?: battleRooms.firstOrNull { it.isNotEmpty() } ?: "arena"

        if (isRefereeConnected) {
            // Join the room if not already joined
            if (!activeRooms.value.contains(roomToUse.lowercase())) {
                webSocketRepository.joinRoom(roomToUse, "REFEREE")
            }
            
            // Send summon message if in broadcast room
            if (broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
                val message = "/me [$turneyTitle] SUMMON MATCH: ${teamA.uppercase()} vs ${teamB.uppercase()}. ALL PLAYERS ENTER ROOM ${roomToUse.uppercase()} NOW!"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
                
                // Start a 3-minute countdown broadcast job into that room
                viewModelScope.launch {
                    var remainingSeconds = 180
                    val normalizedRoomToUse = roomToUse.lowercase()
                    
                    // Track entered participants of teamA and teamB
                    val teamAUser = teamA.firstOrNull()?.lowercase() ?: ""
                    val teamBUser = teamB.firstOrNull()?.lowercase() ?: ""
                    
                    var isTeamAEntered = false
                    var isTeamBEntered = false

                    // Create a collector to listen to room presence events via webSocketRepository.events
                    val presenceCollectorJob = launch {
                        webSocketRepository.events.collect { json ->
                            val type = json["type"]?.jsonPrimitive?.content ?: ""
                            if (type == "room.participant.added") {
                                val currentRoom = json["room"]?.jsonPrimitive?.content?.lowercase() ?: ""
                                val enteringUser = json["username"]?.jsonPrimitive?.content?.lowercase() ?: ""
                                
                                if (currentRoom == normalizedRoomToUse) {
                                    if (enteringUser == teamAUser) isTeamAEntered = true
                                    if (enteringUser == teamBUser) isTeamBEntered = true
                                }
                            }
                        }
                    }

                    try {
                        while (remainingSeconds > 0) {
                            if (isTeamAEntered && isTeamBEntered) {
                                val successMessage = "/me [$turneyTitle] BOTH TEAMS ($teamAUser & $teamBUser) HAVE ENTERED ROOM ${roomToUse.uppercase()}! COUNTDOWN CANCELLED."
                                webSocketRepository.sendMessage(normalizedBroadcastRoom, successMessage, "REFEREE")
                                
                                // Send multi login command template after substitution
                                val formattedMultiMsg = multiLoginTemplate.replace("{room}", roomToUse.uppercase())
                                val multiBroadcastMessage = "/me [$turneyTitle] $formattedMultiMsg"
                                webSocketRepository.sendMessage(normalizedBroadcastRoom, multiBroadcastMessage, "REFEREE")
                                break
                            }

                            val min = remainingSeconds / 60
                            val sec = remainingSeconds % 60
                            val timeStr = if (sec == 0) "$min MINUTES" else "$min MIN $sec SEC"
                            val countdownMessage = "/me [$turneyTitle] MATCH TIME LIMIT COUNTDOWN: $timeStr REMAINING TO ENTER ROOM ${roomToUse.uppercase()}!"
                            webSocketRepository.sendMessage(normalizedBroadcastRoom, countdownMessage, "REFEREE")
                            
                            delay(30000L) // interval 30s
                            remainingSeconds -= 30
                        }
                        
                        if (!(isTeamAEntered && isTeamBEntered)) {
                            val timeUpMessage = "/me [$turneyTitle] TIME IS UP! PREPARING FOR AUTOMATED FORFEIT/START CHECKS IN ROOM ${roomToUse.uppercase()}."
                            webSocketRepository.sendMessage(normalizedBroadcastRoom, timeUpMessage, "REFEREE")
                        }
                    } finally {
                        presenceCollectorJob.cancel()
                    }
                }
            }
        }
        
        return roomToUse
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
                "/me [$turneyTitle] ${username.uppercase()} TRANSFER ${amountCr}CR ✅.$refundText REGISTRATION $count/$total."
            } else {
                "/me [$turneyTitle] ${username.uppercase()} JOINED ✅. REGISTRATION $count/$total."
            }

            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
        }
    }

    private fun sendMatchClosedMessage() {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        val isJoinedInBroadcastRoom = activeRooms.value.contains(normalizedBroadcastRoom)

        if (isRefereeConnected && broadcastRoom.isNotEmpty() && isJoinedInBroadcastRoom) {
            val message = "/me [$turneyTitle] REGISTRATION CLOSED ($bracketSize/$bracketSize) ✅. PREPARING ROLLING PHASE..."

            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
        }
    }

    fun getFirstMatch(): Pair<String, String>? {
        if (registeredParticipants.size < 2) return null
        return Pair(registeredParticipants[0], registeredParticipants[1])
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
    fun updateRegistrationFree(free: Boolean) {
        isRegistrationFree = free
    }

    fun updateRegistrationFee(enabled: Boolean) {
        isRegistrationFeeEnabled = enabled
        isRegistrationFree = !enabled
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

    fun updateTurneyTitle(title: String) {
        turneyTitle = title
        viewModelScope.launch { AuthPreferences.saveTurneyTitle(title) }
    }

    fun updateMultiLoginTemplate(template: String) {
        multiLoginTemplate = template
        viewModelScope.launch { AuthPreferences.saveMultiLoginTemplate(template) }
    }
}
