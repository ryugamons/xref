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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
import id.xterm.xref.ui.theme.RedPucat
import id.xterm.xref.ui.theme.RedPucatBorder
import id.xterm.xref.ui.theme.RedPucatTrans
import id.xterm.xref.ui.theme.TextDim

private enum class HomeSection {
    REFEREE, ROOM, MATCH, SETTINGS
}

@Composable
fun DashboardScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToRooms: () -> Unit = {}
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
                    onNavigateToRooms = onNavigateToRooms
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
                    onNavigateToRooms = onNavigateToRooms
                )
            }
        }
    }
}

@Composable
private fun ContentArea(
    activeSection: HomeSection,
    viewModel: HomeViewModel,
    onNavigateToRooms: () -> Unit
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
        MatchSection(viewModel)
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
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Card: PRIZE TRANSFER
        XrefCard(title = "PRIZE TRANSFER") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                XrefTextField(
                    value = viewModel.transferTargetId,
                    onValueChange = { viewModel.transferTargetId = it },
                    label = "Winner ID",
                    modifier = Modifier.weight(1f)
                )
                XrefTextField(
                    value = viewModel.transferAmountCr,
                    onValueChange = { viewModel.transferAmountCr = it },
                    label = "Amount",
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                XrefButton(
                    text = "SEND",
                    onClick = { viewModel.sendPrizeTransfer() },
                    modifier = Modifier.width(60.dp),
                    height = 42.dp,
                    fontSize = 11.sp,
                    contentPadding = PaddingValues(0.dp),
                    enabled = viewModel.isRefereeConnected && viewModel.transferTargetId.isNotBlank() && viewModel.transferAmountCr.isNotBlank()
                )
            }
        }

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
                    containerColor = if (viewModel.isRefereeConnected) RedPucat else NeonGreen,
                    contentColor = if (viewModel.isRefereeConnected) Color.White else DarkGreen900
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
                    containerColor = if (viewModel.isStarterConnected) RedPucat else NeonGreen,
                    contentColor = if (viewModel.isStarterConnected) Color.White else DarkGreen900
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
    val scrollState = rememberScrollState()
    XrefCard(title = "ROOM SETUP", modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
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
                    containerColor = RedPucat,
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
                        containerColor = RedPucat,
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
                                    .background(RedPucatTrans, RoundedCornerShape(8.dp))
                                    .border(1.dp, RedPucatBorder, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "×",
                                    color = RedPucat,
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
                    containerColor = RedPucat,
                    contentColor = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchSection(
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    var showCancelMatchDialog by remember { mutableStateOf(false) }
    var showAbortTournamentDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showFinishTournamentDialog by remember { mutableStateOf(false) }
    
    var showStateChangeDialog by remember { mutableStateOf<MatchPhase?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importData(context, it) }
    }

    if (showStateChangeDialog != null) {
        val targetPhase = showStateChangeDialog!!
        val (title, msg) = when (targetPhase) {
            MatchPhase.IDLE -> "OPEN REGISTRATION?" to "Start a new tournament match session?"
            MatchPhase.REGISTRATION -> "START ROLL PHASE?" to "Close registration and ask participants to roll?"
            MatchPhase.ROLLING -> "SEED BRACKET?" to "Generate the match bracket based on current roll results?"
            else -> "" to ""
        }
        
        XrefConfirmDialog(
            title = title,
            message = msg,
            onConfirm = {
                when (targetPhase) {
                    MatchPhase.IDLE -> viewModel.toggleMatchRegistration()
                    MatchPhase.REGISTRATION -> viewModel.startRollPhaseManually()
                    MatchPhase.ROLLING -> viewModel.seedBracketManually()
                    else -> {}
                }
                showStateChangeDialog = null
            },
            onDismiss = { showStateChangeDialog = null }
        )
    }

    if (showFinishTournamentDialog) {
        XrefConfirmDialog(
            title = "FINISH TOURNAMENT?",
            message = "Manually set tournament status to FINISHED. This will stop preparation broadcasts.",
            onConfirm = {
                showFinishTournamentDialog = false
                viewModel.finishTournamentManually()
            },
            onDismiss = { showFinishTournamentDialog = false }
        )
    }

    if (showCancelMatchDialog) {
        XrefConfirmDialog(
            title = "CANCEL MATCH?",
            message = "This will refund all registration fees to participants and reset the current match registration.",
            onConfirm = {
                showCancelMatchDialog = false
                viewModel.cancelMatchAndRefund()
            },
            onDismiss = { showCancelMatchDialog = false }
        )
    }

    if (showAbortTournamentDialog) {
        XrefConfirmDialog(
            title = "ABORT TOURNAMENT?",
            message = "This will stop the current tournament progression. NO REFUNDS will be sent automatically.",
            onConfirm = {
                showAbortTournamentDialog = false
                viewModel.toggleMatchRegistration()
            },
            onDismiss = { showAbortTournamentDialog = false }
        )
    }

    if (showClearDataDialog) {
        XrefConfirmDialog(
            title = "RESET TOURNAMENT?",
            message = "This will wipe all registered participants and bracket data. This action CANNOT be undone.",
            onConfirm = {
                showClearDataDialog = false
                viewModel.clearAllMatchData()
            },
            onDismiss = { showClearDataDialog = false }
        )
    }

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
                        modifier = Modifier.width(100.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
            val buttonText = when {
                viewModel.matchPhase == MatchPhase.IDLE -> "OPEN REGISTRATION"
                viewModel.matchPhase == MatchPhase.REGISTRATION -> "START ROLL"
                viewModel.matchPhase == MatchPhase.ROLLING -> if (viewModel.participantRolls.size == viewModel.registeredParticipants.size) "SEED BRACKET" else "WAITING FOR ROLLS (${viewModel.participantRolls.size}/${viewModel.registeredParticipants.size})"
                viewModel.matchPhase == MatchPhase.BRACKET_READY -> "TOURNAMENT IN PROGRESS"
                viewModel.matchPhase == MatchPhase.IN_PROGRESS -> "BATTLE IN PROGRESS"
                viewModel.matchPhase == MatchPhase.FINISHED -> "OPEN NEW TOURNAMENT"
                else -> "OPEN REGISTRATION"
            }
            
            val isButtonEnabled = when {
                viewModel.matchPhase == MatchPhase.IDLE -> viewModel.isRefereeConnected
                viewModel.matchPhase == MatchPhase.REGISTRATION -> viewModel.registeredParticipants.size == viewModel.bracketSize
                viewModel.matchPhase == MatchPhase.ROLLING -> viewModel.participantRolls.size == viewModel.registeredParticipants.size
                viewModel.matchPhase == MatchPhase.BRACKET_READY -> false 
                viewModel.matchPhase == MatchPhase.IN_PROGRESS -> true 
                viewModel.matchPhase == MatchPhase.FINISHED -> true
                else -> false
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                XrefButton(
                    text = buttonText,
                    onClick = { 
                        when (viewModel.matchPhase) {
                            MatchPhase.IDLE -> showStateChangeDialog = MatchPhase.IDLE
                            MatchPhase.REGISTRATION -> showStateChangeDialog = MatchPhase.REGISTRATION
                            MatchPhase.ROLLING -> showStateChangeDialog = MatchPhase.ROLLING
                            MatchPhase.FINISHED -> viewModel.toggleMatchRegistration() // Restarting doesn't strictly need dialog as it resets data
                            else -> {}
                        }
                    },
                    modifier = Modifier.weight(1f),
                    height = 56.dp,
                    enabled = isButtonEnabled,
                    containerColor = when {
                        viewModel.matchPhase == MatchPhase.REGISTRATION -> Color(0xFFFFB300)
                        viewModel.matchPhase == MatchPhase.ROLLING -> Color.Gray
                        viewModel.matchPhase == MatchPhase.BRACKET_READY -> DarkGreen800
                        viewModel.matchPhase == MatchPhase.IN_PROGRESS -> Color(0xFF2196F3)
                        else -> NeonGreen
                    },
                    contentColor = if (viewModel.matchPhase == MatchPhase.BRACKET_READY) TextDim else Color.Black
                )

                if (viewModel.matchPhase == MatchPhase.REGISTRATION) {
                    XrefButton(
                        text = "STOP",
                        onClick = { viewModel.toggleMatchRegistration() },
                        modifier = Modifier.width(80.dp),
                        height = 56.dp,
                        containerColor = RedPucat,
                        contentColor = Color.White
                    )
                }

                if (viewModel.matchPhase == MatchPhase.REGISTRATION && viewModel.registeredParticipants.isNotEmpty()) {
                    XrefButton(
                        text = "CANCEL & REFUND",
                        onClick = { showCancelMatchDialog = true },
                        modifier = Modifier.weight(1f),
                        height = 56.dp,
                        containerColor = RedPucat,
                        contentColor = Color.White,
                        enabled = !viewModel.isProcessingRefund
                    )
                }
            }
            
            if (viewModel.matchPhase == MatchPhase.IN_PROGRESS || viewModel.matchPhase == MatchPhase.BRACKET_READY) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    XrefButton(
                        text = "FINISH TOURNAMENT",
                        onClick = { showFinishTournamentDialog = true },
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        containerColor = DarkGreen700,
                        contentColor = NeonGreen
                    )
                    XrefButton(
                        text = "ABORT (REFUND)",
                        onClick = { showAbortTournamentDialog = true },
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                        containerColor = RedPucat,
                        contentColor = Color.White
                    )
                }
            }
            
            if (viewModel.matchPhase == MatchPhase.IDLE || viewModel.matchPhase == MatchPhase.FINISHED) {
                if (viewModel.registeredParticipants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        XrefButton(
                            text = "RESET",
                            onClick = { showClearDataDialog = true },
                            modifier = Modifier.weight(1f),
                            height = 42.dp,
                            containerColor = Color(0xFF444444),
                            contentColor = Color.White
                        )
                        XrefButton(
                            text = "EXPORT",
                            onClick = { viewModel.exportData() },
                            modifier = Modifier.weight(1f),
                            height = 42.dp,
                            containerColor = DarkGreen800,
                            contentColor = NeonGreen
                        )
                        XrefButton(
                            text = "IMPORT",
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            height = 42.dp,
                            containerColor = DarkGreen800,
                            contentColor = NeonGreen
                        )
                    }
                }
            }
            
            // Manual Add Participant Row
            if (viewModel.matchPhase == MatchPhase.REGISTRATION) {
                Spacer(modifier = Modifier.height(16.dp))
                var newParticipantName by remember { mutableStateOf("") }
                var selectedGroupIndex by remember { mutableIntStateOf(0) }
                var groupDropdownExpanded by remember { mutableStateOf(false) }
                
                val numGroups = if (viewModel.bracketSize > 16) viewModel.bracketSize / 16 else 1
                val participantsInSelectedGroup = viewModel.registeredParticipants.count { (viewModel.participantGroups[it] ?: 0) == selectedGroupIndex }
                val isGroupFull = participantsInSelectedGroup >= 16
                val isBracketFull = viewModel.registeredParticipants.size >= viewModel.bracketSize
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    XrefTextField(
                        value = newParticipantName,
                        onValueChange = { newParticipantName = it },
                        label = "MANUAL TEAM NAME",
                        modifier = Modifier.weight(if (numGroups > 1) 0.5f else 0.7f)
                    )
                    
                    if (numGroups > 1) {
                        Box(modifier = Modifier.weight(0.25f)) {
                            val groupLabel = when(selectedGroupIndex) {
                                0 -> "A"
                                1 -> "B"
                                2 -> "C"
                                3 -> "D"
                                else -> (selectedGroupIndex + 1).toString()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isGroupFull) Color.DarkGray else DarkGreen800)
                                    .border(1.dp, if (isGroupFull) Color.Gray else NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable { groupDropdownExpanded = true }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "GROUP $groupLabel",
                                    color = if (isGroupFull) Color.Gray else NeonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = if (isGroupFull) Color.Gray else NeonGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = groupDropdownExpanded,
                                onDismissRequest = { groupDropdownExpanded = false },
                                modifier = Modifier.background(DarkGreen800).border(1.dp, NeonGreen, RoundedCornerShape(4.dp))
                            ) {
                                for (i in 0 until numGroups) {
                                    val label = when(i) {
                                        0 -> "A"
                                        1 -> "B"
                                        2 -> "C"
                                        3 -> "D"
                                        else -> (i + 1).toString()
                                    }
                                    val countInG = viewModel.registeredParticipants.count { (viewModel.participantGroups[it] ?: 0) == i }
                                    DropdownMenuItem(
                                        text = { 
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("GROUP $label", color = if (countInG >= 16) Color.Gray else NeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                Text("($countInG/16)", color = if (countInG >= 16) RedPucat else NeonGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                                            }
                                        },
                                        onClick = {
                                            selectedGroupIndex = i
                                            groupDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    XrefButton(
                        text = "+ ADD",
                        onClick = {
                            if (newParticipantName.isNotBlank() && !isGroupFull && !isBracketFull) {
                                viewModel.addParticipant(newParticipantName, selectedGroupIndex)
                                newParticipantName = ""
                            }
                        },
                        modifier = Modifier.weight(0.25f),
                        height = 42.dp,
                        fontSize = 11.sp,
                        enabled = newParticipantName.isNotBlank() && !isGroupFull && !isBracketFull
                    )
                }
            }

            // Participants List Cards
            if (viewModel.registeredParticipants.isNotEmpty()) {
                if (viewModel.bracketSize > 16) {
                    // Grouped Layout for large brackets
                    val numGroups = viewModel.bracketSize / 16
                    for (gIdx in 0 until numGroups) {
                        val groupParticipants = viewModel.registeredParticipants.filter { 
                            (viewModel.participantGroups[it] ?: 0) == gIdx 
                        }
                        
                        if (groupParticipants.isNotEmpty()) {
                            val groupLabel = when(gIdx) {
                                0 -> "A"
                                1 -> "B"
                                2 -> "C"
                                3 -> "D"
                                else -> (gIdx + 1).toString()
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            XrefCard(title = "GROUP $groupLabel PARTICIPANTS (${groupParticipants.size}/16)") {
                                ParticipantsList(viewModel, groupParticipants)
                            }
                        }
                    }
                } else {
                    // Single list for 16 or fewer participants
                    Spacer(modifier = Modifier.height(16.dp))
                    XrefCard(title = "PARTICIPANTS (${viewModel.registeredParticipants.size}/${viewModel.bracketSize})") {
                        ParticipantsList(viewModel, viewModel.registeredParticipants)
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
fun ParticipantsList(viewModel: HomeViewModel, participants: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        participants.forEach { participant ->
            val index = viewModel.registeredParticipants.indexOf(participant)
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
                        if (newValue.isNotBlank() && !viewModel.registeredParticipants.contains(newValue)) {
                            val oldRoll = viewModel.participantRolls.remove(participant)
                            val oldGroup = viewModel.participantGroups.remove(participant)
                            viewModel.registeredParticipants[index] = newValue
                            if (oldGroup != null) viewModel.participantGroups[newValue] = oldGroup
                            if (oldRoll != null) viewModel.participantRolls[newValue] = oldRoll
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
                val canDelete = viewModel.matchPhase == MatchPhase.REGISTRATION || viewModel.matchPhase == MatchPhase.ROLLING
                IconButton(
                    onClick = {
                        viewModel.registeredParticipants.remove(participant)
                        viewModel.participantRolls.remove(participant)
                        viewModel.participantGroups.remove(participant)
                    },
                    enabled = canDelete,
                    modifier = Modifier
                        .size(42.dp)
                        .background(if (canDelete) RedPucatTrans else Color.Transparent, RoundedCornerShape(8.dp))
                        .border(1.dp, if (canDelete) RedPucatBorder else Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "×",
                        color = if (canDelete) RedPucat else TextDim.copy(alpha = 0.3f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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

            // Auto Leave Starter Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkGreen900)
                    .border(1.dp, DarkGreen700, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = viewModel.isAutoLeaveStarterEnabled,
                    onCheckedChange = { viewModel.updateAutoLeaveStarter(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonGreen,
                        uncheckedColor = TextDim,
                        checkmarkColor = DarkBackground
                    )
                )
                Text(
                    text = "AUTO LEAVE STARTER",
                    color = if (viewModel.isAutoLeaveStarterEnabled) NeonGreen else TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }

            // Auto Broadcast Result Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkGreen900)
                    .border(1.dp, DarkGreen700, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = viewModel.isAutoBroadcastResultEnabled,
                    onCheckedChange = { viewModel.updateAutoBroadcastResult(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonGreen,
                        uncheckedColor = TextDim,
                        checkmarkColor = DarkBackground
                    )
                )
                Text(
                    text = "AUTO BROADCAST RESULT",
                    color = if (viewModel.isAutoBroadcastResultEnabled) NeonGreen else TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
            
            XrefButton(
                text = "SAVE SETTINGS",
                onClick = {
                    val newValue = localIntervalText.toIntOrNull() ?: 60
                    viewModel.updateBroadcastInterval(newValue)
                    viewModel.updateTurneyTitle(localTitleText.ifBlank { "XREF" })
                    viewModel.updateMultiLoginTemplate(localTemplateText.ifBlank { "BRING YOUR 10 MULTI-IDS INTO ROOM {room} NOW!" })
                    viewModel.saveTemplates(
                        readyCheck = localReadyCheck.ifBlank { "[REFEREE] Are you ready? Reply 'rd' to confirm!" }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = localIntervalText.isNotEmpty()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "XREF v1.01",
                color = NeonGreen.copy(alpha = 0.3f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun XrefConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Text(
                text = message,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        },
        confirmButton = {
            XrefButton(
                text = "CONFIRM",
                onClick = onConfirm,
                modifier = Modifier.width(100.dp),
                height = 36.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = RedPucat, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = DarkBackground,
        shape = RoundedCornerShape(8.dp)
    )
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

