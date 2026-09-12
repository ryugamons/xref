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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.TextDim

private enum class HomeSection {
    REFEREE, ROOM, TEAM, SETTINGS
}

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    var activeSection by remember { mutableStateOf(HomeSection.REFEREE) }

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
                    icon = Icons.Rounded.Groups,
                    label = "Team",
                    isActive = activeSection == HomeSection.TEAM,
                    onClick = { activeSection = HomeSection.TEAM },
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

        // Content Area with Animations
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
            RoomSection(viewModel, connectionState)
        }

        AnimatedVisibility(
            visible = activeSection == HomeSection.TEAM,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.weight(1f)
        ) {
            TeamSection(viewModel)
        }

        AnimatedVisibility(
            visible = activeSection == HomeSection.SETTINGS,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SettingsSection()
        }
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
                    onValueChange = { newId -> viewModel.refereeId = newId },
                    label = "Referee ID"
                )
                Spacer(modifier = Modifier.height(6.dp))
                XrefTextField(
                    value = viewModel.password,
                    onValueChange = { newPassword -> viewModel.password = newPassword },
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
                    containerColor = if (viewModel.isRefereeConnected) androidx.compose.ui.graphics.Color(0xFFFF5252) else id.xterm.xref.ui.theme.NeonGreen,
                    contentColor = if (viewModel.isRefereeConnected) androidx.compose.ui.graphics.Color.Black else id.xterm.xref.ui.theme.DarkGreen900
                )
            }

            // Card 2: BROADCAST
            XrefCard(
                title = "BROADCAST",
                modifier = Modifier.weight(1f)
            ) {
                XrefTextField(
                    value = viewModel.broadcastId,
                    onValueChange = { newBroadcastId -> viewModel.broadcastId = newBroadcastId },
                    label = "Broadcast ID"
                )
                Spacer(modifier = Modifier.height(6.dp))
                XrefTextField(
                    value = viewModel.broadcastPassword,
                    onValueChange = { newBroadcastPassword -> viewModel.broadcastPassword = newBroadcastPassword },
                    label = "Password",
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(6.dp))
                XrefButton(
                    text = if (viewModel.isBroadcastConnected) "LOGOUT" else "LOGIN",
                    onClick = {
                        if (viewModel.isBroadcastConnected) {
                            viewModel.disconnectBroadcast()
                        } else {
                            viewModel.connectBroadcast()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isBroadcastConnecting,
                    containerColor = if (viewModel.isBroadcastConnected) androidx.compose.ui.graphics.Color(0xFFFF5252) else id.xterm.xref.ui.theme.NeonGreen,
                    contentColor = if (viewModel.isBroadcastConnected) androidx.compose.ui.graphics.Color.Black else id.xterm.xref.ui.theme.DarkGreen900
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
        
        // Broadcast Status Card
        StatusCard(
            id = viewModel.broadcastId,
            status = viewModel.broadcastStatusText,
            credits = viewModel.broadcastCredits,
            isConnected = viewModel.isBroadcastConnected
        )
    }
}

@Composable
private fun RoomSection(viewModel: HomeViewModel, connectionState: ConnectionState) {
    XrefCard(title = "ROOM_CONFIG") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            XrefTextField(
                value = viewModel.roomId,
                onValueChange = { newRoomId -> viewModel.roomId = newRoomId },
                label = "Room Name/ID",
                modifier = Modifier.weight(1f)
            )
            XrefButton(
                text = "JOIN",
                onClick = { viewModel.joinRoom() },
                modifier = Modifier.width(80.dp),
                enabled = connectionState is ConnectionState.Connected
            )
        }
    }
}

@Composable
private fun TeamSection(viewModel: HomeViewModel) {
    XrefCard(title = "PARTICIPANTS_LIST", modifier = Modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            XrefButton(
                text = "8 TEAMS ${if (viewModel.bracketSize == 8) "✔" else ""}",
                onClick = { viewModel.changeBracketSize(8) },
                modifier = Modifier.weight(1f),
                enabled = viewModel.bracketSize != 8
            )
            XrefButton(
                text = "16 TEAMS ${if (viewModel.bracketSize == 16) "✔" else ""}",
                onClick = { viewModel.changeBracketSize(16) },
                modifier = Modifier.weight(1f),
                enabled = viewModel.bracketSize != 16
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        ) {
            itemsIndexed(viewModel.participants) { index, participant ->
                XrefTextField(
                    value = participant,
                    onValueChange = { newValue -> viewModel.updateParticipant(index, newValue) },
                    label = "Team ${index + 1}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection() {
    XrefCard(title = "SYSTEM SETTINGS") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SYSTEM SETTINGS",
                color = NeonGreen.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
}
