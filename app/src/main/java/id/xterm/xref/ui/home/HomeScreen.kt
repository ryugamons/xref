package id.xterm.xref.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.xterm.xref.ui.components.XrefButton
import id.xterm.xref.ui.components.XrefCard
import id.xterm.xref.ui.components.XrefTextField
import id.xterm.xref.ui.components.StatusCard
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.DarkGreen700
import id.xterm.xref.ui.theme.DarkGreen800
import id.xterm.xref.ui.theme.DarkGreen900
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.TextDim

private enum class HomeSection {
    REFEREE, ROOM, MATCH, SETTINGS
}

@Composable
fun DashboardScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToRooms: () -> Unit = {},
    onLoadToDashboard: (teamA: List<String>, teamB: List<String>) -> Unit = { _, _ -> }
) {
    if (viewModel.showLicenseDialog) {
        LicenseDialog(viewModel)
    }

    if (!viewModel.isAuthorized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "LICENSE REQUIRED TO ACCESS TERMINAL",
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    var activeSection by remember { mutableStateOf(HomeSection.REFEREE) }
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Side: 2x2 Icon Grid
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    GridItem(
                        icon = Icons.Rounded.Person,
                        label = "Referee",
                        isActive = activeSection == HomeSection.REFEREE,
                        onClick = { activeSection = HomeSection.REFEREE },
                        modifier = Modifier.weight(1f)
                    )
                    GridItem(
                        icon = Icons.Rounded.MeetingRoom,
                        label = "Room",
                        isActive = activeSection == HomeSection.ROOM,
                        onClick = { activeSection = HomeSection.ROOM },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    GridItem(
                        icon = Icons.Rounded.SportsScore,
                        label = "Match",
                        isActive = activeSection == HomeSection.MATCH,
                        onClick = { activeSection = HomeSection.MATCH },
                        modifier = Modifier.weight(1f)
                    )
                    GridItem(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        isActive = activeSection == HomeSection.SETTINGS,
                        onClick = { activeSection = HomeSection.SETTINGS },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Right Side: Dynamic Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ContentArea(
                    activeSection = activeSection,
                    viewModel = viewModel,
                    onNavigateToRooms = onNavigateToRooms,
                    onLoadToDashboard = onLoadToDashboard
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(8.dp)
        ) {
            // 2x2 Icon Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    GridItem(
                        icon = Icons.Rounded.Person,
                        label = "Referee",
                        isActive = activeSection == HomeSection.REFEREE,
                        onClick = { activeSection = HomeSection.REFEREE },
                        modifier = Modifier.weight(1f)
                    )
                    GridItem(
                        icon = Icons.Rounded.MeetingRoom,
                        label = "Room",
                        isActive = activeSection == HomeSection.ROOM,
                        onClick = { activeSection = HomeSection.ROOM },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    GridItem(
                        icon = Icons.Rounded.SportsScore,
                        label = "Match",
                        isActive = activeSection == HomeSection.MATCH,
                        onClick = { activeSection = HomeSection.MATCH },
                        modifier = Modifier.weight(1f)
                    )
                    GridItem(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        isActive = activeSection == HomeSection.SETTINGS,
                        onClick = { activeSection = HomeSection.SETTINGS },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Content Area
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                ContentArea(
                    activeSection = activeSection,
                    viewModel = viewModel,
                    onNavigateToRooms = onNavigateToRooms,
                    onLoadToDashboard = onLoadToDashboard
                )
            }
        }
    }
}

@Composable
private fun ContentArea(
    activeSection: HomeSection,
    viewModel: HomeViewModel,
    onNavigateToRooms: () -> Unit,
    onLoadToDashboard: (List<String>, List<String>) -> Unit
) {
    AnimatedVisibility(
        visible = activeSection == HomeSection.REFEREE,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        RefereeSection(viewModel)
    }

    AnimatedVisibility(
        visible = activeSection == HomeSection.ROOM,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        RoomSection(viewModel, onNavigateToRooms)
    }

    AnimatedVisibility(
        visible = activeSection == HomeSection.MATCH,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        MatchSection(viewModel, onLoadToDashboard)
    }

    AnimatedVisibility(
        visible = activeSection == HomeSection.SETTINGS,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        SettingsSection(viewModel)
    }
}

@Composable
private fun GridItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) DarkGreen800 else Color.Transparent)
            .border(
                1.dp,
                if (isActive) NeonGreen else DarkGreen700,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) NeonGreen else NeonGreen.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            color = if (isActive) NeonGreen else NeonGreen.copy(alpha = 0.6f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RefereeSection(viewModel: HomeViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Card 1: REFEREE
            XrefCard(
                title = "REFEREE",
                modifier = Modifier.weight(1f)
            ) {
                XrefTextField(
                    value = viewModel.refereeId,
                    onValueChange = { viewModel.refereeId = it },
                    label = "Referee ID"
                )
                Spacer(modifier = Modifier.height(6.dp))
                XrefTextField(
                    value = viewModel.refereePassword,
                    onValueChange = { viewModel.refereePassword = it },
                    label = "Password",
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(6.dp))
                XrefButton(
                    text = if (viewModel.isRefereeConnected) "LOGOUT" else "LOGIN",
                    onClick = {
                        if (viewModel.isRefereeConnected) {
                            viewModel.disconnectReferee()
                        } else {
                            viewModel.connectReferee()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isRefereeConnecting,
                    containerColor = if (viewModel.isRefereeConnected) Color(0xFFFF5252) else NeonGreen,
                    contentColor = if (viewModel.isRefereeConnected) Color.Black else DarkGreen900
                )
            }

            // Card 2: STARTER
            XrefCard(
                title = "STARTER",
                modifier = Modifier.weight(1f)
            ) {
                XrefTextField(
                    value = viewModel.starterId,
                    onValueChange = { viewModel.starterId = it },
                    label = "Starter ID"
                )
                Spacer(modifier = Modifier.height(6.dp))
                XrefTextField(
                    value = viewModel.starterPassword,
                    onValueChange = { viewModel.starterPassword = it },
                    label = "Password",
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(6.dp))
                XrefButton(
                    text = if (viewModel.isStarterConnected) "LOGOUT" else "LOGIN",
                    onClick = {
                        if (viewModel.isStarterConnected) {
                            viewModel.disconnectStarter()
                        } else {
                            viewModel.connectStarter()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isStarterConnecting,
                    containerColor = if (viewModel.isStarterConnected) Color(0xFFFF5252) else NeonGreen,
                    contentColor = if (viewModel.isStarterConnected) Color.Black else DarkGreen900
                )
            }
        }
        
        // Referee Status Card
        StatusCard(
            id = viewModel.refereeId,
            status = viewModel.refereeStatusText,
            credits = viewModel.refereeCredits,
            isConnected = viewModel.isRefereeConnected
        )
        
        // Starter Status Card
        StatusCard(
            id = viewModel.starterId,
            status = viewModel.starterStatusText,
            credits = viewModel.starterCredits,
            isConnected = viewModel.isStarterConnected
        )
    }
}

@Composable
private fun RoomSection(viewModel: HomeViewModel, onNavigateToRooms: () -> Unit) {
    XrefCard(title = "ROOM SETUP") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Broadcast Room Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                XrefTextField(
                    value = viewModel.broadcastRoom,
                    onValueChange = { viewModel.updateBroadcastRoom(it) },
                    label = "Broadcast Room",
                    modifier = Modifier.weight(1f)
                )
                XrefButton(
                    text = "JOIN",
                    onClick = { 
                        viewModel.joinBroadcastRoom()
                        onNavigateToRooms()
                    },
                    modifier = Modifier.width(54.dp),
                    height = 42.dp,
                    enabled = viewModel.isRefereeConnected,
                    contentPadding = PaddingValues(horizontal = 0.dp)
                )
                XrefButton(
                    text = "LEAVE",
                    onClick = { viewModel.leaveBroadcastRoom() },
                    modifier = Modifier.width(54.dp),
                    height = 42.dp,
                    enabled = viewModel.isRefereeConnected,
                    containerColor = Color(0xFF8B2525),
                    contentColor = Color.White,
                    contentPadding = PaddingValues(horizontal = 0.dp)
                )
                
                // Alignment placeholder to match Battle Room's remove button slot
                Box(modifier = Modifier.size(38.dp))
            }

            // Dynamic Battle Rooms List
            viewModel.battleRooms.forEachIndexed { index, room ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    XrefTextField(
                        value = room,
                        onValueChange = { viewModel.updateBattleRoom(index, it) },
                        label = "Battle Room ${index + 1}",
                        modifier = Modifier.weight(1f)
                    )
                    
                    XrefButton(
                        text = "JOIN",
                        onClick = { 
                            viewModel.joinBattleRoom(index)
                            onNavigateToRooms()
                        },
                        modifier = Modifier.width(54.dp),
                        height = 42.dp,
                        enabled = viewModel.isRefereeConnected,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    )

                    XrefButton(
                        text = "LEAVE",
                        onClick = { viewModel.leaveBattleRoom(index) },
                        modifier = Modifier.width(54.dp),
                        height = 42.dp,
                        enabled = viewModel.isRefereeConnected,
                        containerColor = Color(0xFF8B2525),
                        contentColor = Color.White,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    )
                    
                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.BottomCenter) {
                        if (viewModel.battleRooms.size > 1) {
                            IconButton(
                                onClick = { viewModel.removeBattleRoom(index) },
                                modifier = Modifier
                                    .padding(bottom = 2.dp)
                                    .size(38.dp)
                                    .background(Color(0x22FF5252), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0x44FF5252), RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "×",
                                    color = Color(0xFFFF5252),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                XrefButton(
                    text = "ADD ROOM",
                    onClick = { viewModel.addBattleRoom() },
                    modifier = Modifier.weight(1f),
                    containerColor = DarkGreen700,
                    contentColor = NeonGreen
                )
                XrefButton(
                    text = "JOIN ALL",
                    onClick = { 
                        viewModel.joinRooms()
                        onNavigateToRooms()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.isRefereeConnected
                )
                XrefButton(
                    text = "LEAVE ALL",
                    onClick = { viewModel.leaveRooms() },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.isRefereeConnected,
                    containerColor = Color(0xFF8B2525),
                    contentColor = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchSection(
    viewModel: HomeViewModel,
    onLoadToDashboard: (List<String>, List<String>) -> Unit
) {
    XrefCard(title = "OPEN MATCH SETUP", modifier = Modifier.fillMaxHeight()) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dropdown for Team Count
            var expanded by remember { mutableStateOf(false) }
            val options = listOf(2, 4, 8, 16, 32, 64)
            
            Column {
                Text(
                    text = "TEAM COUNT",
                    color = TextDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "${viewModel.bracketSize} TEAMS",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = NeonGreen,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkGreen700,
                            focusedContainerColor = DarkGreen900,
                            unfocusedContainerColor = DarkGreen900
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(DarkGreen800)
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "$option TEAMS", 
                                        color = NeonGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    ) 
                                },
                                onClick = {
                                    viewModel.changeBracketSize(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Registration Fee Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkGreen900)
                    .border(1.dp, DarkGreen700, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = viewModel.isRegistrationFeeEnabled,
                    onCheckedChange = { viewModel.updateRegistrationFee(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonGreen,
                        uncheckedColor = TextDim,
                        checkmarkColor = DarkBackground
                    )
                )
                Text(
                    text = "REGISTRATION FEE",
                    color = if (viewModel.isRegistrationFeeEnabled) NeonGreen else TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                
                if (viewModel.isRegistrationFeeEnabled) {
                    XrefTextField(
                        value = viewModel.registrationFeeNominal,
                        onValueChange = { viewModel.updateRegistrationFeeNominal(it) },
                        label = "CREDITS",
                        modifier = Modifier.width(100.dp)
                    )
                } else {
                    Text(
                        text = "FREE",
                        color = NeonGreen.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DYNAMIC MATCH BUTTON
            val buttonText = when (viewModel.matchPhase) {
                MatchPhase.IDLE -> "OPEN REGISTRATION"
                MatchPhase.REGISTRATION -> "START ROLL"
                MatchPhase.ROLLING -> if (viewModel.participantRolls.size == viewModel.registeredParticipants.size) "SEED BRACKET" else "WAITING FOR ROLLS (${viewModel.participantRolls.size}/${viewModel.registeredParticipants.size})"
                MatchPhase.BRACKET_READY -> "START BATTLE"
                MatchPhase.IN_PROGRESS -> "BATTLE IN PROGRESS"
                MatchPhase.FINISHED -> "TOURNAMENT FINISHED"
            }
            
            val isButtonEnabled = when (viewModel.matchPhase) {
                MatchPhase.IDLE -> viewModel.isRefereeConnected
                MatchPhase.REGISTRATION -> viewModel.registeredParticipants.size == viewModel.bracketSize
                MatchPhase.ROLLING -> viewModel.participantRolls.size == viewModel.registeredParticipants.size
                MatchPhase.BRACKET_READY -> true
                MatchPhase.IN_PROGRESS -> true // To allow manual finish if needed
                MatchPhase.FINISHED -> true
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                XrefButton(
                    text = buttonText,
                    onClick = { 
                        when (viewModel.matchPhase) {
                            MatchPhase.IDLE -> viewModel.toggleMatchRegistration()
                            MatchPhase.REGISTRATION -> viewModel.startRollPhaseManually()
                            MatchPhase.ROLLING -> viewModel.seedBracketManually()
                            MatchPhase.BRACKET_READY -> {
                                viewModel.getFirstMatch()?.let { (a, b) ->
                                    onLoadToDashboard(listOf(a), listOf(b))
                                }
                            }
                            MatchPhase.IN_PROGRESS -> viewModel.finishTournamentManually()
                            MatchPhase.FINISHED -> viewModel.toggleMatchRegistration()
                            else -> {}
                        }
                    },
                    modifier = Modifier.weight(1f),
                    height = 56.dp,
                    enabled = isButtonEnabled,
                    containerColor = when (viewModel.matchPhase) {
                        MatchPhase.REGISTRATION -> Color(0xFFFFB300)
                        MatchPhase.ROLLING, MatchPhase.BRACKET_READY -> Color.Gray
                        MatchPhase.IN_PROGRESS -> Color(0xFF2196F3)
                        else -> NeonGreen
                    },
                    contentColor = Color.Black
                )

                if (viewModel.matchPhase == MatchPhase.REGISTRATION && viewModel.registeredParticipants.isNotEmpty()) {
                    XrefButton(
                        text = "CANCEL MATCH",
                        onClick = { viewModel.cancelMatchAndRefund() },
                        modifier = Modifier.weight(1f),
                        height = 56.dp,
                        containerColor = Color(0xFF8B2525),
                        contentColor = Color.White
                    )
                }
            }
            
            if (viewModel.matchPhase == MatchPhase.IN_PROGRESS || viewModel.matchPhase == MatchPhase.BRACKET_READY) {
                Spacer(modifier = Modifier.height(8.dp))
                XrefButton(
                    text = "ABORT TOURNAMENT",
                    onClick = { viewModel.toggleMatchRegistration() },
                    modifier = Modifier.fillMaxWidth(),
                    height = 42.dp,
                    containerColor = Color(0xFF8B2525),
                    contentColor = Color.White
                )
            }
            
            if (viewModel.matchPhase == MatchPhase.IDLE || viewModel.matchPhase == MatchPhase.FINISHED) {
                if (viewModel.registeredParticipants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    XrefButton(
                        text = "CLEAR ALL DATA",
                        onClick = { viewModel.clearAllMatchData() },
                        modifier = Modifier.fillMaxWidth(),
                        height = 42.dp,
                        containerColor = Color(0xFF444444),
                        contentColor = Color.White
                    )
                }
            }
            
            // Manual Add Participant Row
            if (viewModel.matchPhase == MatchPhase.REGISTRATION) {
                Spacer(modifier = Modifier.height(16.dp))
                var newParticipantName by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    XrefTextField(
                        value = newParticipantName,
                        onValueChange = { newParticipantName = it },
                        label = "MANUAL TEAM NAME",
                        modifier = Modifier.weight(0.7f)
                    )
                    XrefButton(
                        text = "+ ADD",
                        onClick = {
                            viewModel.addParticipant(newParticipantName)
                            newParticipantName = ""
                        },
                        modifier = Modifier.weight(0.3f),
                        height = 42.dp,
                        enabled = newParticipantName.isNotBlank() && viewModel.registeredParticipants.size < viewModel.bracketSize
                    )
                }
            }

            // Participants List Card
            if (viewModel.registeredParticipants.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                XrefCard(title = "REGISTERED PARTICIPANTS (${viewModel.registeredParticipants.size}/${viewModel.bracketSize})") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        viewModel.registeredParticipants.forEachIndexed { index, participant ->
                            val roll = viewModel.participantRolls[participant]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Editable Name
                                XrefTextField(
                                    value = participant,
                                    onValueChange = { newValue -> 
                                        val oldRoll = viewModel.participantRolls.remove(participant)
                                        viewModel.registeredParticipants[index] = newValue
                                        if (oldRoll != null) {
                                            viewModel.participantRolls[newValue] = oldRoll
                                        }
                                    },
                                    label = "NAME",
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Editable Roll
                                XrefTextField(
                                    value = roll ?: "",
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                            viewModel.updateParticipantRoll(participant, newValue)
                                        }
                                    },
                                    label = "ROLL",
                                    modifier = Modifier.width(90.dp),
                                    borderColor = if (roll != null && viewModel.duplicateRolls.contains(roll))
                                        Color(0xFFFFA500)
                                    else DarkGreen700
                                )
                                
                                // Remove Participant Button
                                IconButton(
                                    onClick = { 
                                        viewModel.registeredParticipants.removeAt(index)
                                        viewModel.participantRolls.remove(participant)
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0x22FF5252), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0x44FF5252), RoundedCornerShape(8.dp))
                                ) {
                                    Text(
                                        text = "×",
                                        color = Color(0xFFFF5252),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (viewModel.matchPhase != MatchPhase.IDLE) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (viewModel.matchPhase) {
                        MatchPhase.REGISTRATION -> "BROADCASTING EVERY ${viewModel.broadcastIntervalSeconds}S..."
                        MatchPhase.ROLLING -> "WAITING FOR PARTICIPANTS TO TYPE /roll"
                        else -> "MATCH SETUP COMPLETED"
                    },
                    color = NeonGreen.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(viewModel: HomeViewModel) {
    var localIntervalText by remember(viewModel.broadcastIntervalSeconds) {
        mutableStateOf(viewModel.broadcastIntervalSeconds.toString())
    }
    var localTitleText by remember(viewModel.turneyTitle) {
        mutableStateOf(viewModel.turneyTitle)
    }
    var localTemplateText by remember(viewModel.multiLoginTemplate) {
        mutableStateOf(viewModel.multiLoginTemplate)
    }
    var localMatchCall by remember(viewModel.matchCallTemplate) {
        mutableStateOf(viewModel.matchCallTemplate)
    }
    var localReadyCheck by remember(viewModel.readyCheckTemplate) {
        mutableStateOf(viewModel.readyCheckTemplate)
    }

    val scrollState = rememberScrollState()

    XrefCard(title = "SYSTEM SETTINGS", modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            XrefTextField(
                value = localTitleText,
                onValueChange = { localTitleText = it },
                label = "TURNEY TITLE (BROADCAST HEADER)"
            )

            XrefTextField(
                value = localMatchCall,
                onValueChange = { localMatchCall = it },
                label = "MATCH CALL TEMPLATE"
            )

            XrefTextField(
                value = localTemplateText,
                onValueChange = { localTemplateText = it },
                label = "BRING MULTI-IDS TEMPLATE"
            )

            XrefTextField(
                value = localReadyCheck,
                onValueChange = { localReadyCheck = it },
                label = "READY CHECK TEMPLATE"
            )

            XrefTextField(
                value = viewModel.walletPin,
                onValueChange = { viewModel.updateWalletPin(it) },
                label = "WALLET PIN",
                visualTransformation = PasswordVisualTransformation()
            )
            
            XrefTextField(
                value = localIntervalText,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        localIntervalText = newValue
                    }
                },
                label = "BROADCAST INTERVAL (SECONDS)"
            )
            
            XrefButton(
                text = "SAVE SETTINGS",
                onClick = {
                    val newValue = localIntervalText.toIntOrNull() ?: 60
                    viewModel.updateBroadcastInterval(newValue)
                    viewModel.updateTurneyTitle(localTitleText.ifBlank { "XREF" })
                    viewModel.updateMultiLoginTemplate(localTemplateText.ifBlank { "BRING YOUR 10 MULTI-IDS INTO ROOM {room} NOW!" })
                    viewModel.saveTemplates(
                        matchCall = localMatchCall.ifBlank { "[BROADCAST] Match starting: {teamA} vs {teamB}. Enter room: {room}" },
                        readyCheck = localReadyCheck.ifBlank { "[REFEREE] Are you ready? Reply 'rd' to confirm!" }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = localIntervalText.isNotEmpty()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "XREF TERMINAL v1.0.0",
                color = NeonGreen.copy(alpha = 0.3f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun LicenseDialog(viewModel: HomeViewModel) {
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { /* Force license registration */ },
        title = {
            Text(
                text = "ACTIVATE TERMINAL",
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHALLENGE KEY:",
                        color = TextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Text(
                        text = "COPY",
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { 
                                clipboardManager.setText(AnnotatedString(viewModel.challengeText))
                            }
                            .padding(4.dp)
                    )
                }

                Text(
                    text = viewModel.challengeText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkGreen900)
                        .padding(8.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                XrefTextField(
                    value = viewModel.licenseInput,
                    onValueChange = { viewModel.licenseInput = it },
                    label = "INPUT LICENSE KEY"
                )
                
                if (viewModel.licenseErrorMessage != null) {
                    Text(
                        text = viewModel.licenseErrorMessage!!,
                        color = Color.Red,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            XrefButton(
                text = "ACTIVATE",
                onClick = { viewModel.registerLicense() },
                modifier = Modifier.width(120.dp),
                height = 42.dp
            )
        },
        containerColor = DarkBackground,
        titleContentColor = NeonGreen,
        textContentColor = Color.White,
        shape = RoundedCornerShape(8.dp)
    )
}

