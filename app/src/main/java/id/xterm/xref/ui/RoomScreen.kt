package id.xterm.xref.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.ui.components.ChatView
import id.xterm.xref.ui.components.XrefButton
import id.xterm.xref.ui.components.XrefTextField
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.DarkGreen800
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.TextDim

@Composable
fun RoomScreen(viewModel: HomeViewModel) {
    val activeRooms by viewModel.activeRooms.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Left Side: Room List
            Box(modifier = Modifier.width(300.dp)) {
                RoomList(
                    rooms = activeRooms.toList(),
                    viewModel = viewModel,
                    onRoomClick = { viewModel.selectedRoomInRoomsTab = it }
                )
            }
            
            // Vertical Divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(NeonGreen.copy(alpha = 0.1f))
            )

            // Right Side: Chat Detail
            Box(modifier = Modifier.weight(1f)) {
                val selectedRoom = viewModel.selectedRoomInRoomsTab
                if (selectedRoom != null) {
                    val messages = viewModel.roomMessagesMap[selectedRoom] ?: emptyList()
                    RoomChatDetail(
                        roomName = selectedRoom,
                        messages = messages,
                        viewModel = viewModel,
                        onBack = { viewModel.selectedRoomInRoomsTab = null },
                        showBackButton = false
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "SELECT A ROOM TO START CHATTING",
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
            targetState = viewModel.selectedRoomInRoomsTab,
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
                    viewModel = viewModel,
                    onRoomClick = { viewModel.selectedRoomInRoomsTab = it }
                )
            } else {
                val messages = viewModel.roomMessagesMap[room] ?: emptyList()
                RoomChatDetail(
                    roomName = room,
                    messages = messages,
                    viewModel = viewModel,
                    onBack = { viewModel.selectedRoomInRoomsTab = null },
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
                    text = "NO ACTIVE ROOMS\nJOIN A ROOM FROM DASHBOARD",
                    color = TextDim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        ChatView(
            messages = messages,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Message Input Area
        var inputText by remember { mutableStateOf("") }
        
        fun sendMessage() {
            if (inputText.isNotBlank()) {
                viewModel.sendRoomMessage(roomName, inputText)
                inputText = ""
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                modifier = Modifier.width(64.dp),
                height = 42.dp,
                contentPadding = PaddingValues(horizontal = 0.dp)
            )
        }
    }
}
