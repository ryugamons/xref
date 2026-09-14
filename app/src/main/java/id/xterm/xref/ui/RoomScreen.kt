package id.xterm.xref.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.core.match.MatchParticipant
import id.xterm.xref.core.match.MatchSide
import id.xterm.xref.core.match.MatchState
import id.xterm.xref.ui.components.ChatView
import id.xterm.xref.ui.components.XrefButton
import id.xterm.xref.ui.components.XrefCard
import id.xterm.xref.ui.components.XrefTextField
import id.xterm.xref.ui.arena.ArenaViewModel
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.DarkGreen800
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.TextDim
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

@Composable
fun RoomScreen(
    homeViewModel: HomeViewModel,
    arenaViewModel: ArenaViewModel
) {
    val activeRooms by homeViewModel.activeRooms.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Left Side: Room List
            Box(modifier = Modifier.width(280.dp)) {
                RoomList(
                    rooms = activeRooms.toList(),
                    viewModel = homeViewModel,
                    onRoomClick = { 
                        homeViewModel.selectedRoomInRoomsTab = it 
                        arenaViewModel.selectedRoom = it
                    }
                )
            }
            
            // Vertical Divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(NeonGreen.copy(alpha = 0.1f))
            )

            // Right Side: Chat Detail + Arena
            Box(modifier = Modifier.weight(1f)) {
                val selectedRoom = homeViewModel.selectedRoomInRoomsTab
                if (selectedRoom != null) {
                    val messages = homeViewModel.roomMessagesMap[selectedRoom] ?: emptyList()
                    RoomChatDetail(
                        roomName = selectedRoom,
                        messages = messages,
                        homeViewModel = homeViewModel,
                        arenaViewModel = arenaViewModel,
                        onBack = { 
                            homeViewModel.selectedRoomInRoomsTab = null 
                            arenaViewModel.selectedRoom = null
                        },
                        showBackButton = false
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "SELECT A ROOM TO START MONITORING",
                            color = TextDim,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    } else {
        AnimatedContent(
            targetState = homeViewModel.selectedRoomInRoomsTab,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "RoomNavigation"
        ) { room ->
            if (room == null) {
                RoomList(
                    rooms = activeRooms.toList(),
                    viewModel = homeViewModel,
                    onRoomClick = { 
                        homeViewModel.selectedRoomInRoomsTab = it 
                        arenaViewModel.selectedRoom = it
                    }
                )
            } else {
                val messages = homeViewModel.roomMessagesMap[room] ?: emptyList()
                RoomChatDetail(
                    roomName = room,
                    messages = messages,
                    homeViewModel = homeViewModel,
                    arenaViewModel = arenaViewModel,
                    onBack = { 
                        homeViewModel.selectedRoomInRoomsTab = null 
                        arenaViewModel.selectedRoom = null
                    },
                    showBackButton = true
                )
            }
        }
    }
}

@Composable
private fun RoomList(rooms: List<String>, viewModel: HomeViewModel, onRoomClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Text(
            text = "ACTIVE ROOMS",
            color = NeonGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (rooms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO ACTIVE ROOMS\nJOIN A ROOM FROM HOME",
                    color = TextDim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rooms) { room ->
                    val messages = viewModel.roomMessagesMap[room]
                    val lastMessage = messages?.lastOrNull()
                    RoomItem(
                        name = room, 
                        lastChat = lastMessage?.let { "${it.username}: ${it.text}" },
                        onClick = { onRoomClick(room) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomItem(name: String, lastChat: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkGreen800)
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ChatBubble,
            contentDescription = null,
            tint = NeonGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.uppercase(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (lastChat != null) {
                Text(
                    text = if (lastChat.length > 40) lastChat.take(37) + "..." else lastChat,
                    color = TextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
        Text(
            text = "VIEW >",
            color = NeonGreen,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RoomChatDetail(
    roomName: String,
    messages: List<id.xterm.xref.core.websocket.ChatMessage>,
    homeViewModel: HomeViewModel,
    arenaViewModel: ArenaViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val session = arenaViewModel.matchManager.getSession(roomName)
    val matchState by (session?.state ?: MutableStateFlow(MatchState.Idle)).collectAsState()
    val teamA by (session?.teamA ?: MutableStateFlow(MatchSide("Team A", emptyList()))).collectAsState()
    val teamB by (session?.teamB ?: MutableStateFlow(MatchSide("Team B", emptyList()))).collectAsState()
    
    var isArenaExpanded by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(8.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = NeonGreen
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = roomName.uppercase(),
                color = NeonGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            
            // Arena Toggle
            IconButton(onClick = { isArenaExpanded = !isArenaExpanded }) {
                Icon(
                    imageVector = if (isArenaExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle Arena",
                    tint = NeonGreen
                )
            }
        }

        // Arena Content (Timer and Teams)
        if (isArenaExpanded || isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TimerSection(matchState)
                }
                if (isLandscape || isArenaExpanded) {
                    TeamListColumn("TEAM A", teamA.participants, Modifier.weight(1f))
                    TeamListColumn("TEAM B", teamB.participants, Modifier.weight(1f))
                }
            }
        }

        Row(modifier = Modifier.weight(1f)) {
            ChatView(
                messages = messages,
                modifier = Modifier.weight(1f)
            )
        }

        // Message Input Area
        var inputText by remember { mutableStateOf("") }
        var dropdownExpanded by remember { mutableStateOf(false) }
        
        fun sendMessage() {
            if (inputText.isNotBlank()) {
                homeViewModel.sendRoomMessage(roomName, inputText)
                inputText = ""
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart).padding(bottom = 2.dp)) {
                XrefButton(
                    text = "▼",
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.width(38.dp),
                    height = 42.dp,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    containerColor = DarkGreen800,
                    contentColor = NeonGreen
                )
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(DarkGreen800).border(1.dp, NeonGreen, RoundedCornerShape(4.dp))
                ) {
                    val templates = listOf(
                        "Match Call" to arenaViewModel.matchCallTemplate,
                        "Bring IDs" to homeViewModel.multiLoginTemplate,
                        "Ready Check" to arenaViewModel.readyCheckTemplate,
                        "Kickoff" to arenaViewModel.kickoffWarningTemplate
                    )
                    templates.forEach { (label, value) ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = label.uppercase(), 
                                    color = NeonGreen, 
                                    fontSize = 11.sp, 
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ) 
                            },
                            onClick = {
                                dropdownExpanded = false
                                arenaViewModel.sendTemplate(value.replace("{room}", roomName.uppercase()))
                            }
                        )
                    }
                }
            }

            XrefTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = "Message",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() })
            )
            XrefButton(
                text = "SEND",
                onClick = { sendMessage() },
                modifier = Modifier.width(54.dp),
                height = 42.dp,
                contentPadding = PaddingValues(horizontal = 0.dp)
            )
        }
    }
}

@Composable
fun TimerSection(state: MatchState) {
    val remaining = if (state is MatchState.Running) state.timeRemainingMillis else 0L
    val seconds = remaining / 1000f
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = String.format(Locale.US, "%.1fs", seconds),
            color = if (seconds < 1.0f && seconds > 0) Color.Red else NeonGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = when(state) {
                is MatchState.Idle -> "READY"
                is MatchState.Running -> "BATTLE"
                is MatchState.Ended -> "ENDED"
                is MatchState.Result -> "WIN: ${state.winner}"
            },
            color = NeonGreen,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TeamListColumn(title: String, participants: List<MatchParticipant>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Text(
            text = title,
            color = NeonGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        participants.take(10).forEach { p ->
            Text(
                text = "${if (p.hasVoted) "●" else "○"} ${p.id.take(8)}",
                color = if (p.hasVoted) NeonGreen else NeonGreen.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}
