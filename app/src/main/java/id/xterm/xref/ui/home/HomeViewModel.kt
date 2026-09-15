package id.xterm.xref.ui.home

import android.util.Base64
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.xterm.core.license.ChallengeUtil
import id.xterm.core.security.SecurityManager
import id.xterm.xref.XrefApplication
import id.xterm.xref.core.match.MatchManager
import id.xterm.xref.core.websocket.ChatMessage
import id.xterm.xref.data.repository.ConnectionState
import id.xterm.xref.data.repository.WebSocketRepository
import id.xterm.xref.data.storage.AuthPreferences
import kotlinx.serialization.json.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MatchPhase {
    IDLE, REGISTRATION, ROLLING, BRACKET_READY, IN_PROGRESS, FINISHED
}

data class ScheduledMatch(
    val nameA: String,
    val nameB: String,
    val phase: String
)

class HomeViewModel : ViewModel() {
    private val webSocketRepository = WebSocketRepository.getInstance()
    private val securityManager = SecurityManager.getInstance()
    val matchManager = MatchManager.getInstance(webSocketRepository)

    var refereeId by mutableStateOf("")
    var refereePassword by mutableStateOf("")
    var starterId by mutableStateOf("")
    var starterPassword by mutableStateOf("")
    
    // License State
    var isAuthorized by mutableStateOf(false)
    var showLicenseDialog by mutableStateOf(false)
    var challengeText by mutableStateOf("")
    var licenseInput by mutableStateOf("")
    var licenseErrorMessage by mutableStateOf<String?>(null)

    // Participant Selection State
    val roomParticipants = webSocketRepository.roomParticipants
    val selectedTeamAIds = mutableStateListOf<String>()
    val selectedTeamBIds = mutableStateListOf<String>()
    var teamSelectionDialogVisible by mutableStateOf(false)
    var currentSelectingTeamName by mutableStateOf("") // "TEAM A" or "TEAM B"

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
    var broadcastIntervalSeconds by mutableStateOf(120)
    var turneyTitle by mutableStateOf("KleponXclub")
    var multiLoginTemplate by mutableStateOf("Please enter your troop to {room} NOW!")
    var readyCheckTemplate by mutableStateOf("/me Are you ready to Fvck?")
    var isAutoLeaveStarterEnabled by mutableStateOf(false)

    // Match Active State
    var matchPhase by mutableStateOf(MatchPhase.IDLE)
    val registeredParticipants = mutableStateListOf<String>()
    val participantRolls = mutableStateMapOf<String, String>()
    val participantsWhoMustReRoll = mutableStateListOf<String>()
    
    val scheduledMatches = mutableStateMapOf<String, ScheduledMatch>()

    // Bracket State
    val bracketScores = mutableStateMapOf<String, Pair<String, String>>() // Key: "RoundName_MatchIndex", Value: (ScoreA, ScoreB)

    var isSummoning by mutableStateOf(false)
    var activeSummonTeams by mutableStateOf<Pair<String, String>?>(null)
    private var summonJob: Job? = null

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

    val activeRooms = webSocketRepository.activeRooms
    private val _roomMessagesMap = mutableStateMapOf<String, SnapshotStateList<ChatMessage>>()
    val roomMessagesMap: Map<String, List<ChatMessage>> = _roomMessagesMap

    var selectedRoomInRoomsTab by mutableStateOf<String?>(null)

    // Prize Transfer State
    var transferTargetId by mutableStateOf("")
    var transferAmountCr by mutableStateOf("")

    init {
        viewModelScope.launch {
            refereeId = AuthPreferences.getRefereeId()
            refereePassword = AuthPreferences.getRefereePassword()
            starterId = AuthPreferences.getStarterId()
            starterPassword = AuthPreferences.getStarterPassword()
            
            broadcastRoom = AuthPreferences.getBroadcastRoom()
            matchManager.mainBroadcastRoom = broadcastRoom
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
            readyCheckTemplate = AuthPreferences.getReadyCheckTemplate()
            isAutoLeaveStarterEnabled = AuthPreferences.getAutoLeaveStarter()
            
            checkLicense()
        }

        viewModelScope.launch {
            webSocketRepository.refereeConnectionState.collect { state ->
                isRefereeConnected = state is ConnectionState.Connected
                isRefereeConnecting = state is ConnectionState.Connecting
                
                when (state) {
                    is ConnectionState.Connected -> refereeStatusText = "idle"
                    is ConnectionState.Connecting -> refereeStatusText = "connecting"
                    is ConnectionState.Disconnected, ConnectionState.Idle -> {
                        refereeStatusText = "offline"
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

        viewModelScope.launch {
            webSocketRepository.subscribeEvents().collect { json ->
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

        viewModelScope.launch {
            webSocketRepository.refereeWalletBalance.collect { balance ->
                refereeCredits = balance
            }
        }

        viewModelScope.launch {
            webSocketRepository.starterWalletBalance.collect { balance ->
                starterCredits = balance
            }
        }

        viewModelScope.launch {
            webSocketRepository.subscribeRoomMessages().collect { pair ->
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

    private suspend fun checkLicense() {
        val storedSignature = AuthPreferences.getLicenseSignature()
        val storedCanaryB64 = AuthPreferences.getLicenseCanary()
        
        val context = XrefApplication.getContext()
        val androidId = ChallengeUtil.getAndroidId(context)
        val challenge = ChallengeUtil.computeSerial("XREF_USER", androidId)
        challengeText = challenge

        if (storedSignature != null && storedCanaryB64 != null) {
            val canaryBlob = try { Base64.decode(storedCanaryB64, Base64.DEFAULT) } catch (e: Exception) { null }
            if (canaryBlob != null) {
                val valid = securityManager.activateAndVerify("XREF_USER", challenge, storedSignature, canaryBlob)
                if (valid) {
                    isAuthorized = true
                    showLicenseDialog = false
                    return
                }
            }
        }
        
        isAuthorized = false
        showLicenseDialog = true
    }

    fun registerLicense() {
        val cleanedLicense = licenseInput.replace(Regex("\\s+"), "")
        if (cleanedLicense.isEmpty()) {
            licenseErrorMessage = "PLEASE INPUT KEY"
            return
        }

        val parts = cleanedLicense.split(".")
        if (parts.size != 2) {
            licenseErrorMessage = "INVALID LICENSE."
            return
        }
        val (sigB64, canaryB64) = parts
        val canaryBlob = try { Base64.decode(canaryB64, Base64.DEFAULT) } catch (e: Exception) {
            licenseErrorMessage = "INVALID LICENSE."
            return
        }

        val context = XrefApplication.getContext()
        val androidId = ChallengeUtil.getAndroidId(context)
        val challenge = ChallengeUtil.computeSerial("XREF_USER", androidId)

        val valid = securityManager.activateAndVerify("XREF_USER", challenge, sigB64, canaryBlob)
        if (valid) {
            viewModelScope.launch {
                AuthPreferences.saveLicense(sigB64, canaryB64)
                isAuthorized = true
                showLicenseDialog = false
            }
        } else {
            licenseErrorMessage = "INVALID LICENSE."
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
            matchPhase = MatchPhase.REGISTRATION
            startBroadcastingStatus()
        } else {
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
                                    webSocketRepository.sendMessage(
                                        normalizedBroadcastRoom,
                                        "/me [SYSTEM] Refunding ${feeMilliCr / 1000}CR to ${participant.uppercase()}...",
                                        "REFEREE"
                                    )
                                    webSocketRepository.sendTransfer(participant, feeMilliCr, walletPin, "REFEREE")
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
        scheduledMatches.clear()
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

    private fun checkRegistrationFull() { }

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
    
    fun callMatchSummon(teamA: String, teamB: String, phase: String = "MATCH"): String? {
        if (isSummoning) return null
        
        matchPhase = MatchPhase.IN_PROGRESS
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        
        val matchesInProgress = matchManager.activeSessions.value.map { it.lowercase() }

        val roomToUse = battleRooms.firstOrNull { 
            it.isNotEmpty() && 
            it.lowercase() != normalizedBroadcastRoom &&
            !matchesInProgress.contains(it.lowercase())
        } ?: battleRooms.firstOrNull { it.isNotEmpty() } ?: "arena"

        scheduledMatches[roomToUse.lowercase()] = ScheduledMatch(teamA, teamB, phase)

        if (isRefereeConnected) {
            if (!activeRooms.value.contains(roomToUse.lowercase())) {
                webSocketRepository.joinRoom(roomToUse, "REFEREE")
            }
            
            if (broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
                val message = "/me [$turneyTitle] [$phase] [PREPARE] ${teamA.uppercase()} vs ${teamB.uppercase()}!"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
                
                isSummoning = true
                activeSummonTeams = Pair(teamA, teamB)
                summonJob = viewModelScope.launch {
                    // 1-minute Preparation Period
                    delay(60000)

                    var remainingSeconds = 180
                    val normalizedRoomToUse = roomToUse.lowercase()
                    
                    val teamAUser = teamA.lowercase()
                    val teamBUser = teamB.lowercase()
                    
                    var isTeamAEntered = false
                    var isTeamBEntered = false

                    val presenceCollectorJob = launch {
                        webSocketRepository.subscribeEvents().collect { json ->
                            val type = json["type"]?.jsonPrimitive?.content ?: ""
                            val currentRoom = json["room"]?.jsonPrimitive?.content?.lowercase() ?: ""
                            
                            if (currentRoom == normalizedRoomToUse) {
                                if (type == "room.joined") {
                                    val enteringUser = json["username"]?.jsonPrimitive?.content?.lowercase() ?: ""
                                    if (enteringUser == teamAUser) isTeamAEntered = true
                                    if (enteringUser == teamBUser) isTeamBEntered = true
                                    
                                    // Also check initial participants list if present in this packet
                                    val participants = json["participants"]?.jsonArray
                                    participants?.forEach { p ->
                                        val u = p.jsonObject["username"]?.jsonPrimitive?.content?.lowercase() ?: ""
                                        if (u == teamAUser) isTeamAEntered = true
                                        if (u == teamBUser) isTeamBEntered = true
                                    }
                                }
                            }
                        }
                    }

                    try {
                        while (remainingSeconds > 0) {
                            if (isTeamAEntered && isTeamBEntered) {
                                break
                            }

                            val min = remainingSeconds / 60
                            val sec = remainingSeconds % 60
                            val timeStr = "%02d:%02d".format(min, sec) + "m"
                            val countdownMessage = "/me [$turneyTitle] [$phase]\n${teamA.uppercase()} VS ${teamB.uppercase()}\n[$timeStr remaining] ENTER [${roomToUse.uppercase()}] NOW!"
                            webSocketRepository.sendMessage(normalizedBroadcastRoom, countdownMessage, "REFEREE")
                            
                            val interval = if (remainingSeconds > 60) 60 else 30
                            
                            var waited = 0
                            while (waited < interval && !(isTeamAEntered && isTeamBEntered)) {
                                delay(1000)
                                waited += 1
                            }
                            remainingSeconds -= interval
                        }
                        
                        if (remainingSeconds <= 0 && !(isTeamAEntered && isTeamBEntered)) {
                            val dqMessage = when {
                                !isTeamAEntered && isTeamBEntered -> {
                                    "/me [$turneyTitle] [$phase] RESULT [10-0]: ${teamB.uppercase()} WINS vs ${teamA.uppercase()} DIS."
                                }
                                isTeamAEntered && !isTeamBEntered -> {
                                    "/me [$turneyTitle] [$phase] RESULT [10-0]: ${teamA.uppercase()} WINS vs ${teamB.uppercase()} DIS."
                                }
                                else -> {
                                    "/me [$turneyTitle] [$phase] RESULT [DIS]: BOTH TEAMS ${teamA.uppercase()} & ${teamB.uppercase()} FAILED TO ENTER."
                                }
                            }
                            webSocketRepository.sendMessage(normalizedBroadcastRoom, dqMessage, "REFEREE")
                        }
                    } finally {
                        presenceCollectorJob.cancel()
                        isSummoning = false
                        activeSummonTeams = null
                        summonJob = null
                    }
                }
            }
        }
        
        return roomToUse
    }

    fun cancelSummon() {
        summonJob?.cancel()
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
            webSocketRepository.sendMessage(normalizedBroadcastRoom, "/me [$turneyTitle] SUMMON CANCELLED BY REFEREE.", "REFEREE")
        }
        isSummoning = false
        activeSummonTeams = null
        summonJob = null
    }

    fun kickoff(room: String) {
        val normalizedRoom = room.lowercase()
        viewModelScope.launch {
            if (isStarterConnected) {
                webSocketRepository.joinRoom(normalizedRoom, "STARTER")
                delay(500)
                webSocketRepository.sendMessage(normalizedRoom, "/kick $starterId", "STARTER")
                
                val scheduled = scheduledMatches[normalizedRoom]
                val nameA = scheduled?.nameA ?: "TEAM A"
                val nameB = scheduled?.nameB ?: "TEAM B"
                val phase = scheduled?.phase ?: "MATCH"

                val idsA = selectedTeamAIds.toList()
                val idsB = selectedTeamBIds.toList()
                
                matchManager.startMatch(normalizedRoom, phase, nameA, idsA, nameB, idsB)
                
                selectedTeamAIds.clear()
                selectedTeamBIds.clear()

                if (isAutoLeaveStarterEnabled) {
                    delay(1000)
                    webSocketRepository.leaveRoom(normalizedRoom, "STARTER")
                }
            }
        }
    }

    fun starterLeave(room: String) {
        val normalizedRoom = room.lowercase()
        webSocketRepository.leaveRoom(normalizedRoom, "STARTER")
    }

    fun sendTemplate(room: String, templateText: String) {
        val normalizedRoom = room.lowercase()
        val scheduled = scheduledMatches[normalizedRoom]
        val teamAName = scheduled?.nameA ?: "Team A"
        val teamBName = scheduled?.nameB ?: "Team B"
        
        val processedMessage = templateText
            .replace("{room}", room.uppercase())
            .replace("{teamA}", teamAName)
            .replace("{teamB}", teamBName)
            
        webSocketRepository.sendMessage(room, processedMessage, "REFEREE")
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

    fun sendPrizeTransfer() {
        val amount = transferAmountCr.toLongOrNull() ?: return
        if (transferTargetId.isBlank()) return
        
        viewModelScope.launch {
            val success = webSocketRepository.sendTransfer(
                target = transferTargetId,
                milliCr = amount * 1000,
                pin = walletPin,
                connectionType = "REFEREE"
            )
            if (success) {
                transferAmountCr = ""
                transferTargetId = ""
            }
        }
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
        matchManager.mainBroadcastRoom = broadcastRoom
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
        
        if (matchPhase != MatchPhase.IDLE) {
            startBroadcastingStatus()
        }
    }

    fun updateTurneyTitle(title: String) {
        turneyTitle = title
        viewModelScope.launch { AuthPreferences.saveTurneyTitle(title) }
    }

    fun updateAutoLeaveStarter(enabled: Boolean) {
        isAutoLeaveStarterEnabled = enabled
        viewModelScope.launch { AuthPreferences.saveAutoLeaveStarter(enabled) }
    }

    fun updateMultiLoginTemplate(template: String) {
        multiLoginTemplate = template
        viewModelScope.launch { AuthPreferences.saveMultiLoginTemplate(template) }
    }

    fun updateBracketScore(roundName: String, matchIndex: Int, scoreA: String, scoreB: String) {
        bracketScores["${roundName}_$matchIndex"] = Pair(scoreA, scoreB)
    }

    fun toggleParticipantSelection(username: String, forTeamA: Boolean) {
        val list = if (forTeamA) selectedTeamAIds else selectedTeamBIds
        if (list.contains(username)) {
            list.remove(username)
        } else {
            list.add(username)
        }
    }

    fun autoSelectParticipants(filter: String, forTeamA: Boolean) {
        if (filter.length < 3) return
        val room = selectedRoomInRoomsTab?.lowercase() ?: return
        val participants = roomParticipants.value[room] ?: return
        val list = if (forTeamA) selectedTeamAIds else selectedTeamBIds
        
        participants.forEach { u ->
            if (u.contains(filter, ignoreCase = true) && !list.contains(u)) {
                list.add(u)
            }
        }
    }

    fun saveTemplates(readyCheck: String) {
        readyCheckTemplate = readyCheck
        
        viewModelScope.launch {
            AuthPreferences.saveReadyCheckTemplate(readyCheck)
        }
    }
}
