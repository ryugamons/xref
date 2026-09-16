package id.xterm.xref.ui.home

import android.util.Base64
import android.util.Log
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

enum class MatchPhase {
    IDLE, REGISTRATION, ROLLING, BRACKET_READY, IN_PROGRESS, FINISHED
}

data class ScheduledMatch(
    val nameA: String,
    val nameB: String,
    val phase: String
)

data class MatchData(val teamA: String, val teamB: String)
data class RoundData(val title: String, val matches: List<MatchData>)

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
    val selectedIdsA = mutableStateMapOf<String, SnapshotStateList<String>>()
    val selectedIdsB = mutableStateMapOf<String, SnapshotStateList<String>>()
    var teamSelectionDialogVisible by mutableStateOf(false)
    var matchSelectionDialogVisible by mutableStateOf(false)
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

    val summonJobs = mutableStateMapOf<String, Job>()

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

    private val _snackbarMessage = Channel<String>(Channel.CONFLATED)
    val snackbarMessage = _snackbarMessage.receiveAsFlow()

    init {
        matchManager.onMatchFinished = { room ->
            val normalizedRoom = room.lowercase()
            scheduledMatches.remove(normalizedRoom)
            selectedIdsA.remove(normalizedRoom)
            selectedIdsB.remove(normalizedRoom)
        }

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
                                    delay(450) // Safe delay for API calls to prevent flooding
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
        summonJobs.values.forEach { it.cancel() }
        summonJobs.clear()
        selectedIdsA.clear()
        selectedIdsB.clear()
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

    fun getAvailableMatchesFromBracket(): List<Pair<MatchData, String>> {
        val totalSlots = bracketSize
        val participants = registeredParticipants.toList()
        val bracketScores = this.bracketScores

        // Re-calculate bracket to find current state
        var currentNames = participants.toList()
        val allRoundNames = listOf("ROUND OF 64", "ROUND OF 32", "ROUND OF 16", "QUARTER-FINALS", "SEMI-FINALS", "FINAL")
        val startRoundIdx = when (totalSlots) {
            64 -> 0; 32 -> 1; 16 -> 2; 8 -> 3; 4 -> 4; 2 -> 5; else -> 5
        }
        val roundNames = allRoundNames.drop(startRoundIdx)
        var roundSize = totalSlots / 2
        var roundIdx = 0
        
        val available = mutableListOf<Pair<MatchData, String>>()
        val currentlyScheduled = scheduledMatches.values.map { it.nameA to it.nameB }
        val activeSessions = matchManager.activeSessions.value.mapNotNull { matchManager.getSession(it) }
            .map { it.teamA.value.name to it.teamB.value.name }

        while (roundSize >= 1 && roundIdx < roundNames.size) {
            val title = roundNames[roundIdx]
            val matches = (0 until roundSize).map { i ->
                MatchData(
                    currentNames.getOrElse(i * 2) { "T${i * 2 + 1}" },
                    currentNames.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" }
                )
            }
            
            matches.forEachIndexed { i, match ->
                val score = bracketScores["${title}_$i"]
                val hasScore = score != null && score.first != "-" && score.second != "-"
                val isScheduled = currentlyScheduled.any { it.first == match.teamA && it.second == match.teamB }
                val isRunning = activeSessions.any { it.first == match.teamA && it.second == match.teamB }
                
                if (!hasScore && !isScheduled && !isRunning && !match.teamA.startsWith("WINNER") && !match.teamB.startsWith("WINNER") && !match.teamA.startsWith("EMPTY") && !match.teamB.startsWith("EMPTY")) {
                    available.add(match to title)
                }
            }

            currentNames = matches.indices.map { i ->
                val score = bracketScores["${title}_$i"]
                val sA = score?.first?.toIntOrNull() ?: -1
                val sB = score?.second?.toIntOrNull() ?: -1
                if (sA > sB) matches[i].teamA 
                else if (sB > sA) matches[i].teamB 
                else "WINNER ${title}-${i + 1}"
            }
            roundSize /= 2
            roundIdx++
        }
        return available
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
    }

    fun broadcastManualRound(round: RoundData) {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (!isRefereeConnected || broadcastRoom.isEmpty() || !activeRooms.value.contains(normalizedBroadcastRoom)) return

        viewModelScope.launch {
            val pairs = round.matches.map { "${it.teamA.uppercase()} vs ${it.teamB.uppercase()}" }
            if (pairs.isNotEmpty()) {
                val message = "/me [$turneyTitle] [${round.title}] BRACKET: ${pairs.joinToString(" | ")}"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
            }
        }
    }
    
    fun callMatchSummon(room: String, teamA: String, teamB: String, phase: String = "MATCH") {
        val normalizedRoom = room.lowercase()
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        
        if (scheduledMatches.containsKey(normalizedRoom)) return

        matchPhase = MatchPhase.IN_PROGRESS
        scheduledMatches[normalizedRoom] = ScheduledMatch(teamA, teamB, phase)

        if (isRefereeConnected) {
            if (!activeRooms.value.contains(normalizedRoom)) {
                webSocketRepository.joinRoom(normalizedRoom, "REFEREE")
            }
            
            if (broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
                val message = "/me [$turneyTitle] [$phase] [PREPARE] ${teamA.uppercase()} vs ${teamB.uppercase()}!"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
                
                val job = viewModelScope.launch {
                    // 1-minute Preparation Period
                    delay(60000)

                    var remainingSeconds = 180
                    val teamAUser = teamA.lowercase()
                    val teamBUser = teamB.lowercase()
                    
                    var isTeamAEntered = false
                    var isTeamBEntered = false

                    val presenceCollectorJob = launch {
                        webSocketRepository.subscribeEvents().collect { json ->
                            val type = json["type"]?.jsonPrimitive?.content ?: ""
                            val currentRoom = json["room"]?.jsonPrimitive?.content?.lowercase() ?: ""
                            
                            if (currentRoom == normalizedRoom) {
                                if (type == "room.joined") {
                                    val enteringUser = json["username"]?.jsonPrimitive?.content?.lowercase() ?: ""
                                    if (enteringUser == teamAUser) isTeamAEntered = true
                                    if (enteringUser == teamBUser) isTeamBEntered = true
                                    
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
                            val countdownMessage = "/me [$turneyTitle] [$phase]\n${teamA.uppercase()} VS ${teamB.uppercase()}\n[$timeStr remaining] ENTER [${room.uppercase()}] NOW!"
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
                            scheduledMatches.remove(normalizedRoom)
                            selectedIdsA.remove(normalizedRoom)
                            selectedIdsB.remove(normalizedRoom)
                        }
                    } finally {
                        presenceCollectorJob.cancel()
                        summonJobs.remove(normalizedRoom)
                    }
                }
                summonJobs[normalizedRoom] = job
            }
        }
    }

    fun cancelSummon(room: String) {
        val normalizedRoom = room.lowercase()
        summonJobs[normalizedRoom]?.cancel()
        summonJobs.remove(normalizedRoom)
        
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
            webSocketRepository.sendMessage(normalizedBroadcastRoom, "/me [$turneyTitle] SUMMON CANCELLED BY REFEREE.", "REFEREE")
        }
        
        scheduledMatches.remove(normalizedRoom)
        selectedIdsA.remove(normalizedRoom)
        selectedIdsB.remove(normalizedRoom)
    }

    fun abortMatch(room: String) {
        val normalizedRoom = room.lowercase()
        matchManager.stopMatch(normalizedRoom)
        scheduledMatches.remove(normalizedRoom)
        selectedIdsA.remove(normalizedRoom)
        selectedIdsB.remove(normalizedRoom)
        
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
            webSocketRepository.sendMessage(normalizedBroadcastRoom, "/me [$turneyTitle] MATCH IN ${room.uppercase()} ABORTED BY REFEREE.", "REFEREE")
        }
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

                val idsA = selectedIdsA[normalizedRoom]?.toList() ?: emptyList<String>()
                val idsB = selectedIdsB[normalizedRoom]?.toList() ?: emptyList<String>()
                
                matchManager.startMatch(normalizedRoom, phase, nameA, idsA, nameB, idsB)
                
                selectedIdsA.remove(normalizedRoom)
                selectedIdsB.remove(normalizedRoom)

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
                showSnackbar("TRANSFER SUCCESS: $amount CR to $transferTargetId")
                transferAmountCr = ""
                transferTargetId = ""
            } else {
                showSnackbar("TRANSFER FAILED. CHECK LOGS/BALANCE.")
            }
        }
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.send(message)
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

    fun toggleParticipantSelection(username: String, room: String, forTeamA: Boolean) {
        val map = if (forTeamA) selectedIdsA else selectedIdsB
        val list = map.getOrPut(room.lowercase()) { mutableStateListOf() }
        if (list.contains(username)) {
            list.remove(username)
        } else {
            list.add(username)
        }
    }

    fun autoSelectParticipants(filter: String, room: String, forTeamA: Boolean) {
        if (filter.length < 3) return
        val normalizedRoom = room.lowercase()
        val participants = roomParticipants.value[normalizedRoom] ?: return
        val map = if (forTeamA) selectedIdsA else selectedIdsB
        val list = map.getOrPut(normalizedRoom) { mutableStateListOf() }
        
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
