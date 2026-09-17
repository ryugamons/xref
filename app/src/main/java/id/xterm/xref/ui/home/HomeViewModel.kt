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
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

enum class MatchPhase {
    IDLE, REGISTRATION, ROLLING, BRACKET_READY, IN_PROGRESS, FINISHED
}

data class ScheduledMatch(
    val nameA: String,
    val nameB: String,
    val phase: String,
    val matchKey: String = ""
)

data class MatchData(val teamA: String, val teamB: String, val matchKey: String = "")
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
    var currentSelectingTeamName by mutableStateOf("")

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
    var isAutoBroadcastResultEnabled by mutableStateOf(true)

    // Match Active State
    var matchPhase by mutableStateOf(MatchPhase.IDLE)
    val registeredParticipants = mutableStateListOf<String>()
    val participantGroups = mutableStateMapOf<String, Int>()
    val participantRolls = mutableStateMapOf<String, String>()
    val participantsWhoMustReRoll = mutableStateListOf<String>()
    
    val scheduledMatches = mutableStateMapOf<String, ScheduledMatch>()

    // Bracket State
    val bracketScores = mutableStateMapOf<String, Pair<String, String>>()
    val completedMatchResults = mutableStateMapOf<String, MatchManager.MatchResult>()

    val summonJobs = mutableStateMapOf<String, Job>()
    val roomCountdowns = mutableStateMapOf<String, Int>()
    private val countdownJobs = mutableStateMapOf<String, Job>()

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

    // Match Results
    val pendingMatchResults = mutableStateMapOf<String, MatchManager.MatchResult>()

    // Prize Transfer State
    var transferTargetId by mutableStateOf("")
    var transferAmountCr by mutableStateOf("")
    
    // Screenshot & Upload State
    var isUploadingImage by mutableStateOf(false)
    
    // Safety States
    private val processedTransactionIds = mutableSetOf<Long>()
    var isProcessingRefund by mutableStateOf(false)

    private val _snackbarMessage = Channel<String>(Channel.CONFLATED)
    val snackbarMessage = _snackbarMessage.receiveAsFlow()

    init {
        matchManager.onMatchFinished = { result ->
            val normalizedRoom = result.room.lowercase()
            
            // Map result to the correct bracket match key
            val scheduled = scheduledMatches[normalizedRoom]
            if (scheduled != null && scheduled.matchKey.isNotEmpty()) {
                completedMatchResults[scheduled.matchKey] = result
            }
            
            pendingMatchResults[normalizedRoom] = result
            if (isAutoBroadcastResultEnabled) {
                matchManager.broadcastResult(result)
            }
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
            if (savedBattleRooms.isEmpty()) battleRooms.add("") else battleRooms.addAll(savedBattleRooms)

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
            isAutoBroadcastResultEnabled = AuthPreferences.getAutoBroadcastResult()
            checkLicense()
        }

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

        viewModelScope.launch {
            webSocketRepository.subscribeEvents().collect { json ->
                if (json["type"]?.jsonPrimitive?.content == "wallet.updated" && matchPhase == MatchPhase.REGISTRATION) {
                    val usernameInPacket = json["username"]?.jsonPrimitive?.content
                    if (usernameInPacket == refereeId) checkWalletHistoryThrottled()
                    if (!isRegistrationFeeEnabled && usernameInPacket != null && 
                        usernameInPacket != refereeId && usernameInPacket != starterId) {
                        if (!registeredParticipants.contains(usernameInPacket) && registeredParticipants.size < bracketSize) {
                            addParticipant(usernameInPacket)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            webSocketRepository.refereeWalletBalance.collect { balance -> refereeCredits = balance }
        }
        viewModelScope.launch {
            webSocketRepository.starterWalletBalance.collect { balance -> starterCredits = balance }
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
                        updateParticipantRoll(rollMatch.groupValues[1], rollMatch.groupValues[2])
                    }
                }
                if (matchPhase == MatchPhase.REGISTRATION && !isRegistrationFeeEnabled) {
                    if (message.text.trim().equals("JOIN", ignoreCase = true)) {
                        val sender = message.username
                        if (sender != "system" && sender != refereeId && sender != starterId) {
                            addParticipant(sender)
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
                if (valid) { isAuthorized = true; showLicenseDialog = false; return }
            }
        }
        isAuthorized = false; showLicenseDialog = true
    }

    fun registerLicense() {
        val cleanedLicense = licenseInput.replace(Regex("\\s+"), "")
        val parts = cleanedLicense.split(".")
        if (parts.size != 2) { licenseErrorMessage = "INVALID LICENSE."; return }
        val (sigB64, canaryB64) = parts
        val canaryBlob = try { Base64.decode(canaryB64, Base64.DEFAULT) } catch (e: Exception) { null }
        if (canaryBlob == null) { licenseErrorMessage = "INVALID LICENSE."; return }
        val context = XrefApplication.getContext()
        val androidId = ChallengeUtil.getAndroidId(context)
        val challenge = ChallengeUtil.computeSerial("XREF_USER", androidId)
        val valid = securityManager.activateAndVerify("XREF_USER", challenge, sigB64, canaryBlob)
        if (valid) {
            viewModelScope.launch { AuthPreferences.saveLicense(sigB64, canaryB64); isAuthorized = true; showLicenseDialog = false }
        } else { licenseErrorMessage = "INVALID LICENSE." }
    }

    private fun checkWalletHistoryThrottled() {
        historyCheckJob?.cancel()
        historyCheckJob = viewModelScope.launch {
            delay(800)
            val history = webSocketRepository.getWalletHistory("REFEREE")
            val lastTransfer = history?.transactions?.firstOrNull { it.type == "transfer_in" }
            if (lastTransfer != null && !processedTransactionIds.contains(lastTransfer.id)) {
                val sender = lastTransfer.note.replace("Credit transfer from ", "", ignoreCase = true).trim()
                if (sender.isNotEmpty()) { processedTransactionIds.add(lastTransfer.id); processTransfer(sender, lastTransfer.amountMilliCr) }
            }
        }
    }

    fun toggleMatchRegistration() {
        if (matchPhase == MatchPhase.IDLE || matchPhase == MatchPhase.FINISHED) {
            if (!isRefereeConnected) return
            if (matchPhase == MatchPhase.FINISHED) clearAllMatchData()
            matchPhase = MatchPhase.REGISTRATION
            startBroadcastingStatus()
        } else { stopBroadcasting(); matchPhase = MatchPhase.IDLE }
    }

    fun cancelMatchAndRefund() {
        if (isProcessingRefund) return
        isProcessingRefund = true
        viewModelScope.launch {
            try {
                matchPhase = MatchPhase.IDLE; stopBroadcasting()
                val normalizedBroadcastRoom = broadcastRoom.lowercase()
                if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
                    val participantsToRefund = registeredParticipants.toList()
                    webSocketRepository.sendMessage(normalizedBroadcastRoom, "/me [SYSTEM] Match cancelled. Refunding credits...", "REFEREE")
                    if (isRegistrationFeeEnabled) {
                        val feeMilliCr = registrationFeeNominal.toLongOrNull()?.let { it * 1000 } ?: 0L
                        if (feeMilliCr > 0) {
                            participantsToRefund.forEach { participant ->
                                webSocketRepository.sendTransfer(participant, feeMilliCr, walletPin, "REFEREE")
                                delay(450)
                            }
                        }
                    }
                }
                clearAllMatchData()
            } finally { isProcessingRefund = false }
        }
    }

    fun clearAllMatchData() {
        stopBroadcasting(); registeredParticipants.clear(); participantGroups.clear(); participantRolls.clear()
        participantsWhoMustReRoll.clear(); scheduledMatches.clear(); summonJobs.values.forEach { it.cancel() }
        summonJobs.clear(); countdownJobs.values.forEach { it.cancel() }; countdownJobs.clear()
        roomCountdowns.clear(); selectedIdsA.clear(); selectedIdsB.clear(); processedTransactionIds.clear()
        bracketScores.clear(); pendingMatchResults.clear(); matchPhase = MatchPhase.IDLE
    }
    
    fun finishTournamentManually() { matchPhase = MatchPhase.FINISHED }

    fun addParticipant(name: String, groupIndex: Int = -1) {
        val normalizedName = name.trim().lowercase()
        if (normalizedName.isNotBlank() && registeredParticipants.size < bracketSize && matchPhase == MatchPhase.REGISTRATION) {
            if (!registeredParticipants.contains(normalizedName)) {
                registeredParticipants.add(normalizedName)
                
                // Assign group: 16 users per group
                val numGroups = if (bracketSize > 16) bracketSize / 16 else 1
                val targetGroup = if (groupIndex != -1) groupIndex else {
                    (registeredParticipants.size - 1) / 16
                }
                participantGroups[normalizedName] = targetGroup % numGroups
                
                sendRegistrationProgress(normalizedName, 0L, 0L)
            }
        }
    }

    fun startRollPhaseManually() { if (matchPhase == MatchPhase.REGISTRATION) { matchPhase = MatchPhase.ROLLING; sendMatchClosedMessage(); startBroadcastingStatus() } }

    private fun startBroadcastingStatus() {
        broadcastJob?.cancel()
        broadcastJob = viewModelScope.launch {
            while (isActive) {
                val sent = when (matchPhase) {
                    MatchPhase.REGISTRATION -> sendCurrentMatchStatus()
                    MatchPhase.ROLLING -> sendRollInstructions()
                    else -> true
                }
                if (sent) delay(broadcastIntervalSeconds * 1000L) else delay(3000L)
            }
        }
    }

    private fun stopBroadcasting() { broadcastJob?.cancel(); broadcastJob = null }

    fun sendCurrentMatchStatus(): Boolean {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
            val joinedText = registeredParticipants.joinToString(", ")
            val feeText = if (isRegistrationFeeEnabled) "$registrationFeeNominal CR" else "FREE"
            val instruction = if (isRegistrationFeeEnabled) "TRF ID $refereeId" else "Type JOIN to enter!"
            val message = "/me [$turneyTitle] OPEN MATCH $bracketSize USERS\nFEE $feeText - $instruction [$joinedText]"
            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
            return true
        }
        return false
    }

    private fun sendRollInstructions(): Boolean {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
            val pendingRolls = registeredParticipants.filter { !participantRolls.containsKey(it) }
            if (pendingRolls.isNotEmpty()) {
                val message = "/me ALL PARTICIPANTS PLEASE /roll NOW!\nPENDING: [${pendingRolls.joinToString(", ")}]"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
            }
            return true
        }
        return false
    }

    fun seedBracketManually() {
        if (matchPhase == MatchPhase.ROLLING) {
            matchPhase = MatchPhase.BRACKET_READY
            stopBroadcasting()
            applyRollSeeding()
        }
    }

    fun getAvailableMatchesFromBracket(): List<Pair<MatchData, String>> {
        val totalSlots = bracketSize
        val participants = registeredParticipants.toList()
        var currentNames = participants.toList()
        val allRoundNames = listOf("ROUND OF 64", "ROUND OF 32", "ROUND OF 16", "QUARTER-FINALS", "SEMI-FINALS", "FINAL")
        val startRoundIdx = when (totalSlots) { 64 -> 0; 32 -> 1; 16 -> 2; 8 -> 3; 4 -> 4; 2 -> 5; else -> 5 }
        val roundNames = allRoundNames.drop(startRoundIdx)
        var roundSize = totalSlots / 2
        var roundIdx = 0
        val available = mutableListOf<Pair<MatchData, String>>()
        while (roundSize >= 1 && roundIdx < roundNames.size) {
            val title = roundNames[roundIdx]
            val matches = (0 until roundSize).map { i -> 
                MatchData(
                    currentNames.getOrElse(i * 2) { "T${i * 2 + 1}" }, 
                    currentNames.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" },
                    matchKey = "${title}_$i"
                ) 
            }
            matches.forEachIndexed { i, match ->
                val score = bracketScores["${title}_$i"]
                val hasScore = score != null && score.first != "-" && score.second != "-"
                if (!hasScore && !scheduledMatches.containsKey(match.teamA) && !match.teamA.startsWith("WINNER") && !match.teamA.startsWith("EMPTY")) {
                    available.add(match to title)
                }
            }
            currentNames = matches.indices.map { i ->
                val score = bracketScores["${title}_$i"]
                val sA = score?.first?.toIntOrNull() ?: -1
                val sB = score?.second?.toIntOrNull() ?: -1
                if (sA > sB) matches[i].teamA else if (sB > sA) matches[i].teamB else "WINNER ${title}-${i + 1}"
            }.let { winners ->
                if (roundSize > 8) {
                    val half = roundSize / 2
                    val reordered = mutableListOf<String>()
                    for (i in 0 until half) { reordered.add(winners[i]); reordered.add(winners[i + half]) }
                    reordered
                } else winners
            }
            roundSize /= 2; roundIdx++
        }
        return available
    }

    fun updateParticipantRoll(name: String, rollText: String) {
        if (matchPhase != MatchPhase.ROLLING) return
        val rollInt = rollText.toIntOrNull() ?: return
        val normalizedName = name.lowercase()
        if (registeredParticipants.contains(normalizedName)) {
            participantRolls[normalizedName] = rollInt.toString()
        }
    }

    private fun applyRollSeeding() {
        val totalSize = bracketSize
        val numGroups = if (totalSize > 16) totalSize / 16 else 1
        val expectedInGroup = if (totalSize > 16) 16 else totalSize
        val newList = mutableListOf<String>()

        for (g in 0 until numGroups) {
            // 1. Get real participants belonging to this group
            val groupParticipants = registeredParticipants.filter { (participantGroups[it] ?: 0) == g }
                .filter { !it.lowercase().startsWith("empty slot") }

            // 2. Sort them by roll result ASCENDING (Lowest roll is best)
            val sortedReal = groupParticipants.map { it to (participantRolls[it]?.toIntOrNull() ?: 999) }
                .sortedBy { it.second }
                .map { it.first }
                .toMutableList()

            // 3. Fill this specific group to its expected capacity (16 or totalSize)
            while (sortedReal.size < expectedInGroup) {
                sortedReal.add("empty slot ${newList.size + sortedReal.size + 1}")
            }

            // 4. Pair for seeding: 1 vs Last, 2 vs Last-1 ... (Highest vs Lowest)
            val n = sortedReal.size
            for (i in 0 until n / 2) {
                newList.add(sortedReal[i])          // Seed Top
                newList.add(sortedReal[n - 1 - i])  // Seed Bottom
            }
        }

        if (newList.isNotEmpty()) {
            registeredParticipants.clear()
            registeredParticipants.addAll(newList)
            // Update group mapping for the new slots
            registeredParticipants.forEachIndexed { idx, name ->
                participantGroups[name] = idx / 16
            }
        }
    }

    fun broadcastManualRound(round: RoundData) {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (!isRefereeConnected || broadcastRoom.isEmpty() || !activeRooms.value.contains(normalizedBroadcastRoom)) return
        viewModelScope.launch {
            val pairs = round.matches.map { "${it.teamA.uppercase()} vs ${it.teamB.uppercase()}" }
            if (pairs.isNotEmpty()) {
                val message = "/me [${round.title}] BRACKET: ${pairs.joinToString(" | ")}"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
            }
        }
    }
    
    fun callMatchSummon(room: String, teamA: String, teamB: String, phase: String = "MATCH", matchKey: String = "") {
        val normalizedRoom = room.lowercase()
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (scheduledMatches.containsKey(normalizedRoom)) return
        matchPhase = MatchPhase.IN_PROGRESS
        scheduledMatches[normalizedRoom] = ScheduledMatch(teamA, teamB, phase, matchKey)
        if (isRefereeConnected) {
            if (!activeRooms.value.contains(normalizedRoom)) webSocketRepository.joinRoom(normalizedRoom, "REFEREE")
            if (broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
                val message = "/me [PREPARE] ${teamA.uppercase()} VS ${teamB.uppercase()}"
                webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
                summonJobs[normalizedRoom] = viewModelScope.launch {
                    delay(30000)
                    var remainingSeconds = 180
                    val teamAUser = teamA.lowercase(); val teamBUser = teamB.lowercase()
                    val initialInRoom = roomParticipants.value[normalizedRoom] ?: emptyList()
                    var isTeamAEntered = initialInRoom.any { it.equals(teamAUser, ignoreCase = true) }
                    var isTeamBEntered = initialInRoom.any { it.equals(teamBUser, ignoreCase = true) }
                    val presenceCollectorJob = launch {
                        webSocketRepository.subscribeEvents().collect { json ->
                            val type = json["type"]?.jsonPrimitive?.content ?: ""
                            if (json["room"]?.jsonPrimitive?.content?.lowercase() == normalizedRoom) {
                                if (type == "room.joined" || type == "room.participant.added") {
                                    val user = json["username"]?.jsonPrimitive?.content?.lowercase() ?: ""
                                    if (user == teamAUser) isTeamAEntered = true
                                    if (user == teamBUser) isTeamBEntered = true
                                }
                            }
                        }
                    }
                    try {
                        while (remainingSeconds > 0) {
                            val currentInRoom = roomParticipants.value[normalizedRoom] ?: emptyList()
                            val idsA = selectedIdsA[normalizedRoom] ?: emptyList()
                            val idsB = selectedIdsB[normalizedRoom] ?: emptyList()
                            val teamAReady = isTeamAEntered || (idsA.isNotEmpty() && idsA.any { id -> currentInRoom.any { it.equals(id, ignoreCase = true) } })
                            val teamBReady = isTeamBEntered || (idsB.isNotEmpty() && idsB.any { id -> currentInRoom.any { it.equals(id, ignoreCase = true) } })
                            if (teamAReady && teamBReady) break
                            val min = remainingSeconds / 60; val sec = remainingSeconds % 60
                            val timeStr = "%02d:%02d".format(min, sec) + "m"
                            val countdownMessage = "/me [${room.uppercase()}] ${teamA.uppercase()} VS ${teamB.uppercase()}\n[$timeStr]"
                            webSocketRepository.sendMessage(normalizedBroadcastRoom, countdownMessage, "REFEREE")
                            val interval = if (remainingSeconds > 60) 60 else 30
                            var waited = 0
                            while (waited < interval && !(teamAReady && teamBReady)) { delay(1000); waited += 1 }
                            remainingSeconds -= interval
                        }
                    } finally { presenceCollectorJob.cancel(); summonJobs.remove(normalizedRoom) }
                }
            }
        }
    }

    fun cancelSummon(room: String) {
        val normalizedRoom = room.lowercase(); val scheduled = scheduledMatches[normalizedRoom]
        summonJobs[normalizedRoom]?.cancel(); summonJobs.remove(normalizedRoom)
        viewModelScope.launch {
            webSocketRepository.sendMessage(normalizedRoom, "/unlock", "REFEREE")
            if (scheduled != null) {
                webSocketRepository.sendMessage(normalizedRoom, "/unmod ${scheduled.nameA}", "REFEREE")
                webSocketRepository.sendMessage(normalizedRoom, "/unmod ${scheduled.nameB}", "REFEREE")
            }
        }
    }

    fun abortMatch(room: String) {
        val normalizedRoom = room.lowercase(); val scheduled = scheduledMatches[normalizedRoom]
        matchManager.stopMatch(normalizedRoom); scheduledMatches.remove(normalizedRoom)
        selectedIdsA.remove(normalizedRoom); selectedIdsB.remove(normalizedRoom)
        viewModelScope.launch {
            webSocketRepository.sendMessage(normalizedRoom, "/unlock", "REFEREE")
            if (scheduled != null) {
                webSocketRepository.sendMessage(normalizedRoom, "/unmod ${scheduled.nameA}", "REFEREE")
                webSocketRepository.sendMessage(normalizedRoom, "/unmod ${scheduled.nameB}", "REFEREE")
            }
        }
    }

    fun kickoff(room: String) {
        val normalizedRoom = room.lowercase()
        viewModelScope.launch {
            if (isStarterConnected) {
                webSocketRepository.joinRoom(normalizedRoom, "STARTER")
                delay(500); webSocketRepository.sendMessage(normalizedRoom, "/kick $starterId", "STARTER")
                val scheduled = scheduledMatches[normalizedRoom]
                val nameA = scheduled?.nameA ?: "TEAM A"; val nameB = scheduled?.nameB ?: "TEAM B"
                val phase = scheduled?.phase ?: "MATCH"
                
                // Ensure we use a stable copy of selected IDs
                val idsA = selectedIdsA[normalizedRoom]?.toList()?.ifEmpty { listOf(nameA) } ?: listOf(nameA)
                val idsB = selectedIdsB[normalizedRoom]?.toList()?.ifEmpty { listOf(nameB) } ?: listOf(nameB)
                
                Log.d("XREF_BATTLE", "Kickoff in $normalizedRoom: $nameA vs $nameB")
                matchManager.startMatch(normalizedRoom, phase, nameA, idsA, nameB, idsB)
                
                if (isAutoLeaveStarterEnabled) { delay(1000); webSocketRepository.leaveRoom(normalizedRoom, "STARTER") }
            }
        }
    }

    fun starterLeave(room: String) { webSocketRepository.leaveRoom(room.lowercase(), "STARTER") }

    fun startManualCountdown(room: String) {
        val normalizedRoom = room.lowercase(); countdownJobs[normalizedRoom]?.cancel(); roomCountdowns[normalizedRoom] = 180
        countdownJobs[normalizedRoom] = viewModelScope.launch {
            while (isActive && (roomCountdowns[normalizedRoom] ?: 0) > 0) {
                val current = roomCountdowns[normalizedRoom] ?: 0
                if (current == 120 || current == 60) {
                    val min = current / 60
                    webSocketRepository.sendMessage(normalizedRoom, "/me [SYSTEM] $min minutes to enter!", "REFEREE")
                }
                delay(1000); if (current > 0) roomCountdowns[normalizedRoom] = current - 1
            }
            if ((roomCountdowns[normalizedRoom] ?: 0) <= 0) {
                webSocketRepository.sendMessage(normalizedRoom, "/me [SYSTEM] Time is up! Multi-ID failed to enter will be DIS.", "REFEREE")
            }
            countdownJobs.remove(normalizedRoom)
        }
    }

    fun stopManualCountdown(room: String) {
        val normalizedRoom = room.lowercase(); countdownJobs[normalizedRoom]?.cancel()
        countdownJobs.remove(normalizedRoom); roomCountdowns.remove(normalizedRoom)
    }

    fun broadcastDisDecision(room: String, winnerTeam: String?, loserTeam: String?, isBoth: Boolean) {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (!isRefereeConnected || broadcastRoom.isEmpty() || !activeRooms.value.contains(normalizedBroadcastRoom)) return
        val scheduled = scheduledMatches[room.lowercase()]
        viewModelScope.launch {
            val message = when {
                isBoth -> "/me RESULT [DIS]: BOTH TEAMS FAILED TO ENTER."
                winnerTeam != null && loserTeam != null -> "/me RESULT [10-0]: ${winnerTeam.uppercase()} WINS vs ${loserTeam.uppercase()} DIS."
                else -> return@launch
            }
            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
            webSocketRepository.sendMessage(room, "/unlock", "REFEREE")
            if (scheduled != null) {
                webSocketRepository.sendMessage(room, "/unmod ${scheduled.nameA}", "REFEREE")
                webSocketRepository.sendMessage(room, "/unmod ${scheduled.nameB}", "REFEREE")
            }
            stopManualCountdown(room)
            scheduledMatches.remove(room.lowercase()); selectedIdsA.remove(room.lowercase()); selectedIdsB.remove(room.lowercase())
        }
    }

    fun sendTemplate(room: String, templateText: String) {
        val normalizedRoom = room.lowercase()
        if (templateText == multiLoginTemplate) startManualCountdown(normalizedRoom)
        val scheduled = scheduledMatches[normalizedRoom]
        val teamAName = scheduled?.nameA ?: "Team A"; val teamBName = scheduled?.nameB ?: "Team B"
        val processedMessage = templateText.replace("{room}", room.uppercase()).replace("{teamA}", teamAName).replace("{teamB}", teamBName)
        webSocketRepository.sendMessage(room, processedMessage, "REFEREE")
        if (templateText == readyCheckTemplate) {
            viewModelScope.launch {
                delay(1000); webSocketRepository.sendMessage(room, "/mod $teamAName", "REFEREE")
                webSocketRepository.sendMessage(room, "/mod $teamBName", "REFEREE")
            }
        }
    }

    fun processTransfer(sender: String, amountMilliCr: Long) {
        if (matchPhase != MatchPhase.REGISTRATION) return
        val normalizedSender = sender.trim().lowercase()
        val requiredMilliCr = (registrationFeeNominal.toLongOrNull() ?: 0L) * 1000
        if (amountMilliCr >= requiredMilliCr) {
            if (!registeredParticipants.contains(normalizedSender) && registeredParticipants.size < bracketSize) {
                registeredParticipants.add(normalizedSender)
                val numGroups = if (bracketSize > 16) bracketSize / 16 else 1
                participantGroups[normalizedSender] = ((registeredParticipants.size - 1) / 16) % numGroups
                val refundMilliCr = if (requiredMilliCr > 0) amountMilliCr - requiredMilliCr else 0L
                if (refundMilliCr > 0) {
                    viewModelScope.launch { webSocketRepository.sendTransfer(normalizedSender, refundMilliCr, walletPin, "REFEREE") }
                }
                sendRegistrationProgress(normalizedSender, amountMilliCr / 1000, refundMilliCr / 1000)
            }
        }
    }

    private fun sendRegistrationProgress(username: String, amountCr: Long, refundCr: Long = 0L) {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
            val count = registeredParticipants.size; val total = bracketSize
            val refundText = if (refundCr > 0) " ${refundCr}CR REFUNDED." else ""
            val message = if (isRegistrationFeeEnabled) "/me ${username.uppercase()} TRANSFER ${amountCr}CR ✅.$refundText REGISTRATION $count/$total." else "/me ${username.uppercase()} JOINED ✅. REGISTRATION $count/$total."
            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
        }
    }

    private fun sendMatchClosedMessage() {
        val normalizedBroadcastRoom = broadcastRoom.lowercase()
        if (isRefereeConnected && broadcastRoom.isNotEmpty() && activeRooms.value.contains(normalizedBroadcastRoom)) {
            val message = "/me REGISTRATION CLOSED ($bracketSize/$bracketSize) ✅"
            webSocketRepository.sendMessage(normalizedBroadcastRoom, message, "REFEREE")
        }
    }

    fun connectReferee() { viewModelScope.launch { AuthPreferences.saveRefereeAuth(refereeId, refereePassword); webSocketRepository.loginAndConnect(refereeId, refereePassword, "REFEREE") } }
    fun disconnectReferee() { webSocketRepository.disconnectSession("REFEREE"); isRefereeConnected = false; refereeStatusText = "offline"; refereeCredits = "0.00 CR" }
    fun connectStarter() { viewModelScope.launch { AuthPreferences.saveStarterAuth(starterId, starterPassword); webSocketRepository.loginAndConnect(starterId, starterPassword, "STARTER") } }
    fun disconnectStarter() { webSocketRepository.disconnectSession("STARTER"); isStarterConnected = false; starterStatusText = "offline"; starterCredits = "0.00 CR" }

    fun sendPrizeTransfer() {
        val amount = transferAmountCr.toLongOrNull() ?: return
        if (transferTargetId.isBlank()) return
        viewModelScope.launch {
            val success = webSocketRepository.sendTransfer(transferTargetId, amount * 1000, walletPin, "REFEREE")
            if (success) { showSnackbar("TRANSFER SUCCESS: $amount CR to $transferTargetId"); transferAmountCr = ""; transferTargetId = "" } else showSnackbar("TRANSFER FAILED. CHECK LOGS/BALANCE.")
        }
    }

    fun uploadAndSendImage(room: String, base64Data: String) {
        if (isUploadingImage) return
        viewModelScope.launch {
            isUploadingImage = true
            try {
                val filename = "share-${System.currentTimeMillis()}.jpg"
                val response = webSocketRepository.uploadPhoto(filename, base64Data, room, "REFEREE")
                if (response != null && response.status == "uploaded") {
                    webSocketRepository.sendImageMessage(room, response.mediaUrl, response.media.mimeType, response.media.sizeBytes, "REFEREE")
                    showSnackbar("IMAGE SENT TO ${room.uppercase()}")
                } else showSnackbar("UPLOAD FAILED")
            } catch (e: Exception) { showSnackbar("ERROR: ${e.message}") } finally { isUploadingImage = false }
        }
    }

    fun showSnackbar(message: String) { viewModelScope.launch { _snackbarMessage.send(message) } }
    fun updateBattleRoom(index: Int, name: String) { if (index in battleRooms.indices) { battleRooms[index] = name.lowercase(); saveRoomPrefs() } }
    fun addBattleRoom() { battleRooms.add(""); saveRoomPrefs() }
    fun removeBattleRoom(index: Int) { if (battleRooms.size > 1) { battleRooms.removeAt(index); saveRoomPrefs() } }
    fun updateBroadcastRoom(name: String) { broadcastRoom = name.lowercase(); matchManager.mainBroadcastRoom = broadcastRoom; saveRoomPrefs() }
    private fun saveRoomPrefs() { viewModelScope.launch { AuthPreferences.saveRooms(broadcastRoom, battleRooms.toList()) } }
    fun joinBroadcastRoom() { if (broadcastRoom.isNotEmpty()) webSocketRepository.joinRoom(broadcastRoom, "REFEREE") }
    fun leaveBroadcastRoom() { if (broadcastRoom.isNotEmpty()) { webSocketRepository.leaveRoom(broadcastRoom, "REFEREE"); _roomMessagesMap.remove(broadcastRoom.lowercase()); if (selectedRoomInRoomsTab == broadcastRoom.lowercase()) selectedRoomInRoomsTab = null } }
    fun joinBattleRoom(index: Int) { val room = battleRooms.getOrNull(index); if (!room.isNullOrEmpty()) webSocketRepository.joinRoom(room, "REFEREE") }
    fun leaveBattleRoom(index: Int) { val room = battleRooms.getOrNull(index); if (!room.isNullOrEmpty()) { webSocketRepository.leaveRoom(room, "REFEREE"); _roomMessagesMap.remove(room.lowercase()); if (selectedRoomInRoomsTab == room.lowercase()) selectedRoomInRoomsTab = null } }
    fun sendRoomMessage(room: String, message: String) { if (message.isNotEmpty()) webSocketRepository.sendMessage(room, message, "REFEREE") }
    fun sendManualMatchResult(room: String) { val result = pendingMatchResults[room.lowercase()] ?: return; matchManager.broadcastResult(result) }
    fun joinRooms() { if (broadcastRoom.isNotEmpty()) webSocketRepository.joinRoom(broadcastRoom, "REFEREE"); battleRooms.forEach { if (it.isNotEmpty()) webSocketRepository.joinRoom(it, "REFEREE") } }
    fun leaveRooms() { if (broadcastRoom.isNotEmpty()) { webSocketRepository.leaveRoom(broadcastRoom, "REFEREE"); _roomMessagesMap.remove(broadcastRoom.lowercase()) } ; battleRooms.forEach { if (it.isNotEmpty()) { webSocketRepository.leaveRoom(it, "REFEREE"); _roomMessagesMap.remove(it.lowercase()) } } ; selectedRoomInRoomsTab = null }
    fun updateRegistrationFee(enabled: Boolean) { isRegistrationFeeEnabled = enabled; isRegistrationFree = !enabled; saveMatchPrefs() }
    fun updateRegistrationFeeNominal(nominal: String) { registrationFeeNominal = nominal; saveMatchPrefs() }
    fun changeBracketSize(size: Int) { bracketSize = size; saveMatchPrefs() }
    private fun saveMatchPrefs() { viewModelScope.launch { AuthPreferences.saveMatchSettings(bracketSize, isRegistrationFeeEnabled, registrationFeeNominal) } }
    fun updateWalletPin(pin: String) { walletPin = pin; viewModelScope.launch { AuthPreferences.saveWalletPin(pin) } }
    fun updateBroadcastInterval(seconds: Int) { broadcastIntervalSeconds = seconds; viewModelScope.launch { AuthPreferences.saveBroadcastInterval(seconds) }; if (matchPhase != MatchPhase.IDLE) startBroadcastingStatus() }
    fun updateTurneyTitle(title: String) { turneyTitle = title; viewModelScope.launch { AuthPreferences.saveTurneyTitle(title) } }
    fun updateAutoLeaveStarter(enabled: Boolean) { isAutoLeaveStarterEnabled = enabled; viewModelScope.launch { AuthPreferences.saveAutoLeaveStarter(enabled) } }
    fun updateAutoBroadcastResult(enabled: Boolean) { isAutoBroadcastResultEnabled = enabled; viewModelScope.launch { AuthPreferences.saveAutoBroadcastResult(enabled) } }
    fun updateMultiLoginTemplate(template: String) { multiLoginTemplate = template; viewModelScope.launch { AuthPreferences.saveMultiLoginTemplate(template) } }
    fun updateBracketScore(roundName: String, matchIndex: Int, scoreA: String, scoreB: String) { bracketScores["${roundName}_$matchIndex"] = Pair(scoreA, scoreB); checkTournamentFinished() }
    private fun checkTournamentFinished() { val available = getAvailableMatchesFromBracket(); if (available.isEmpty()) { val finalScore = bracketScores["FINAL_0"]; if (finalScore != null && finalScore.first != "-" && finalScore.second != "-") { matchPhase = MatchPhase.FINISHED; stopBroadcasting() } } }
    fun toggleParticipantSelection(username: String, room: String, forTeamA: Boolean) {
        val normalizedRoom = room.lowercase()
        val targetMap = if (forTeamA) selectedIdsA else selectedIdsB
        val otherMap = if (forTeamA) selectedIdsB else selectedIdsA
        
        val targetList = targetMap.getOrPut(normalizedRoom) { mutableStateListOf() }
        val otherList = otherMap[normalizedRoom] ?: emptyList<String>()
        
        if (targetList.contains(username)) {
            targetList.remove(username)
        } else {
            // Only add if NOT already selected by the other team and not full (limit 10)
            val isAlreadyTakenByOther = otherList.any { it.equals(username, ignoreCase = true) }
            if (!isAlreadyTakenByOther && targetList.size < 10) {
                targetList.add(username)
            }
        }
    }

    fun autoSelectParticipants(filter: String, room: String, forTeamA: Boolean) {
        if (filter.length < 2) return 
        val normalizedRoom = room.lowercase()
        val participants = roomParticipants.value[normalizedRoom] ?: return
        
        val targetMap = if (forTeamA) selectedIdsA else selectedIdsB
        val otherMap = if (forTeamA) selectedIdsB else selectedIdsA
        
        val targetList = targetMap.getOrPut(normalizedRoom) { mutableStateListOf() }
        // Fetch a fresh snapshot of what the other team has selected
        val otherList = otherMap[normalizedRoom]?.toList() ?: emptyList<String>()
        
        // Advanced Regex: 
        // 1. (^|.*[._-]) : Starts at beginning OR follows a separator (_, ., -)
        // 2. Regex.escape(filter) : The typed filter
        // 3. [._-]?\d*$ : Optional separator followed by digits at the end
        val smartRegex = "(^|.*[._-])${Regex.escape(filter)}[._-]?\\d*$".toRegex(RegexOption.IGNORE_CASE)
        
        participants.forEach { username ->
            val isAlreadySelectedByTarget = targetList.contains(username)
            val isAlreadySelectedByOther = otherList.any { it.equals(username, ignoreCase = true) }

            if (username.matches(smartRegex) && 
                !isAlreadySelectedByTarget && 
                !isAlreadySelectedByOther) {
                if (targetList.size < 10) {
                    targetList.add(username)
                }
            }
        }
    }
    fun saveTemplates(readyCheck: String) { readyCheckTemplate = readyCheck; viewModelScope.launch { AuthPreferences.saveReadyCheckTemplate(readyCheck); showSnackbar("SETTINGS SAVED") } }

    fun exportData() {
        viewModelScope.launch {
            try {
                val data = buildJsonObject {
                    put("bracketSize", bracketSize)
                    put("participants", JsonArray(registeredParticipants.map { JsonPrimitive(it) }))
                    put("groups", buildJsonObject { participantGroups.forEach { (name, group) -> put(name, group) } })
                    put("rolls", buildJsonObject { participantRolls.forEach { (name, roll) -> put(name, roll) } })
                    put("scores", buildJsonObject {
                        bracketScores.forEach { (key, score) ->
                            put(key, buildJsonObject {
                                put("first", score.first)
                                put("second", score.second)
                            })
                        }
                    })
                }
                val jsonString = data.toString()
                val fileName = "XREF_Export_${System.currentTimeMillis()}.json"
                val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsFolder, fileName)
                FileOutputStream(file).use { it.write(jsonString.toByteArray()) }
                showSnackbar("DATA EXPORTED TO DOWNLOADS: $fileName")
            } catch (e: Exception) { showSnackbar("EXPORT FAILED: ${e.message}") }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw Exception("Empty file")
                val jsonContent = Json.decodeFromString<JsonObject>(content)
                clearAllMatchData()
                bracketSize = jsonContent["bracketSize"]?.jsonPrimitive?.int ?: 8
                jsonContent["participants"]?.jsonArray?.forEach { registeredParticipants.add(it.jsonPrimitive.content) }
                jsonContent["groups"]?.jsonObject?.forEach { (name, group) -> participantGroups[name] = group.jsonPrimitive.int }
                jsonContent["rolls"]?.jsonObject?.forEach { (name, roll) -> participantRolls[name] = roll.jsonPrimitive.content }
                jsonContent["scores"]?.jsonObject?.forEach { (key, scoreObj) ->
                    val sObj = scoreObj.jsonObject
                    bracketScores[key] = Pair(sObj["first"]?.jsonPrimitive?.content ?: "-", sObj["second"]?.jsonPrimitive?.content ?: "-")
                }
                showSnackbar("DATA IMPORTED SUCCESSFULLY")
            } catch (e: Exception) { showSnackbar("IMPORT FAILED: ${e.message}") }
        }
    }
}
