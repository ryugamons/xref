package id.xterm.xref.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.xterm.xref.data.repository.ConnectionState
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
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToRooms: () -> Unit = {}
) {
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
                ContentArea(activeSection = activeSection, viewModel = viewModel, onNavigateToRooms = onNavigateToRooms)
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
                ContentArea(activeSection = activeSection, viewModel = viewModel, onNavigateToRooms = onNavigateToRooms)
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
private fun MatchSection(viewModel: HomeViewModel) {
    XrefCard(title = "OPEN MATCH SETUP", modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

            // OPEN / CLOSE MATCH Button
            XrefButton(
                text = if (viewModel.isMatchOpen) "STOP BROADCAST" else "OPEN REGISTRATION",
                onClick = { viewModel.toggleMatchStatus() },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
                enabled = viewModel.isRefereeConnected,
                containerColor = if (viewModel.isMatchOpen) Color(0xFFFF5252) else NeonGreen,
                contentColor = if (viewModel.isMatchOpen) Color.Black else Color.Black
            )
            
            if (viewModel.isMatchOpen) {
                Text(
                    text = "BROADCASTING EVERY 60S...",
                    color = NeonGreen.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "REGISTERED: ${viewModel.registeredParticipants.size}/${viewModel.bracketSize}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(viewModel: HomeViewModel) {
    XrefCard(title = "SYSTEM SETTINGS") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            XrefTextField(
                value = viewModel.walletPin,
                onValueChange = { viewModel.updateWalletPin(it) },
                label = "WALLET PIN",
                visualTransformation = PasswordVisualTransformation()
            )
            
            XrefTextField(
                value = viewModel.broadcastIntervalSeconds.toString(),
                onValueChange = { 
                    val newValue = it.toIntOrNull() ?: 60
                    viewModel.updateBroadcastInterval(newValue)
                },
                label = "BROADCAST INTERVAL (SECONDS)"
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
