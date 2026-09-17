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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.compose.ui.platform.LocalContext
import id.xterm.xref.core.match.MatchSide
import id.xterm.xref.ui.components.ChatView
import id.xterm.xref.ui.components.XrefButton
import id.xterm.xref.ui.components.XrefTextField
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.DarkGreen800
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.RedPucat
import id.xterm.xref.ui.theme.RedPucatTrans
import id.xterm.xref.ui.theme.TextDim
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun RoomScreen(
    homeViewModel: HomeViewModel
) {
    val activeRooms by homeViewModel.activeRooms.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.width(280.dp)) {
                RoomList(
                    rooms = activeRooms.toList(),
                    viewModel = homeViewModel,
                    onRoomClick = { 
                        homeViewModel.selectedRoomInRoomsTab = it 
                    }
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(NeonGreen.copy(alpha = 0.1f))
            )

            Box(modifier = Modifier.weight(1f)) {
                val selectedRoom = homeViewModel.selectedRoomInRoomsTab
                if (selectedRoom != null) {
                    val messages = homeViewModel.roomMessagesMap[selectedRoom] ?: emptyList()
                    RoomChatDetail(
                        roomName = selectedRoom,
                        messages = messages,
                        homeViewModel = homeViewModel,
                        onBack = { 
                            homeViewModel.selectedRoomInRoomsTab = null 
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
                    }
                )
            } else {
                val messages = homeViewModel.roomMessagesMap[room] ?: emptyList()
                RoomChatDetail(
                    roomName = room,
                    messages = messages,
                    homeViewModel = homeViewModel,
                    onBack = { 
                        homeViewModel.selectedRoomInRoomsTab = null 
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
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val normalizedRoom = roomName.lowercase()
    val session = homeViewModel.matchManager.getSession(normalizedRoom)
    val isSummoning = homeViewModel.summonJobs.containsKey(normalizedRoom)
    val scheduled = homeViewModel.scheduledMatches[normalizedRoom]
    val pendingResult = homeViewModel.pendingMatchResults[normalizedRoom]

    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                homeViewModel.uploadAndSendImage(roomName, base64Data)
            } catch (e: Exception) {
                homeViewModel.showSnackbar("PICK ERROR: ${e.message}")
            }
        }
    }

    var showDisDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(4.dp)
    ) {
        // CONSOLIDATED HEADER ROW
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = NeonGreen)
                }
            }
            
            Text(
                text = roomName.uppercase(),
                color = NeonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(if (scheduled == null && session == null) 1f else 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // CALL / CANCEL Button
            if (isSummoning) {
                XrefButton(
                    text = "STOPCALL",
                    onClick = { homeViewModel.cancelSummon(roomName) },
                    modifier = Modifier.width(60.dp),
                    height = 26.dp,
                    fontSize = 8.sp,
                    containerColor = RedPucat,
                    contentColor = Color.White,
                    contentPadding = PaddingValues(0.dp)
                )
            } else if (session == null && scheduled == null) {
                XrefButton(
                    text = "CALL",
                    onClick = { homeViewModel.matchSelectionDialogVisible = true },
                    modifier = Modifier.width(50.dp),
                    height = 26.dp,
                    fontSize = 9.sp,
                    containerColor = NeonGreen,
                    contentColor = Color.Black,
                    contentPadding = PaddingValues(0.dp)
                )
            }

            // Match UI (Team A vs Team B)
            if (scheduled != null || session != null) {
                val sessionA by (session?.teamA ?: MutableStateFlow<MatchSide?>(null)).collectAsState()
                val sessionB by (session?.teamB ?: MutableStateFlow<MatchSide?>(null)).collectAsState()
                
                val nameA = sessionA?.name ?: scheduled?.nameA ?: "TIM A"
                val nameB = sessionB?.name ?: scheduled?.nameB ?: "TIM B"

                val selectedA = homeViewModel.selectedIdsA[normalizedRoom] ?: emptyList<String>()
                val selectedB = homeViewModel.selectedIdsB[normalizedRoom] ?: emptyList<String>()
                
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    XrefButton(
                        text = nameA,
                        onClick = { 
                            homeViewModel.currentSelectingTeamName = "TEAM A"
                            homeViewModel.teamSelectionDialogVisible = true 
                        },
                        modifier = Modifier.weight(1f),
                        height = 26.dp,
                        fontSize = 8.sp,
                        containerColor = if (selectedA.size == 10) NeonGreen else if (selectedA.isNotEmpty()) Color.Yellow.copy(alpha = 0.4f) else DarkGreen800,
                        contentColor = if (selectedA.size == 10) Color.Black else NeonGreen,
                        contentPadding = PaddingValues(0.dp)
                    )
                    
                    Text("vs", color = NeonGreen.copy(alpha = 0.5f), fontSize = 8.sp)

                    XrefButton(
                        text = nameB,
                        onClick = { 
                            homeViewModel.currentSelectingTeamName = "TEAM B"
                            homeViewModel.teamSelectionDialogVisible = true 
                        },
                        modifier = Modifier.weight(1f),
                        height = 26.dp,
                        fontSize = 8.sp,
                        containerColor = if (selectedB.size == 10) NeonGreen else if (selectedB.isNotEmpty()) Color.Yellow.copy(alpha = 0.4f) else DarkGreen800,
                        contentColor = if (selectedB.size == 10) Color.Black else NeonGreen,
                        contentPadding = PaddingValues(0.dp)
                    )

                    if (!isSummoning && session == null) {
                        IconButton(onClick = { homeViewModel.abortMatch(roomName) }, modifier = Modifier.size(24.dp)) {
                            Text("×", color = RedPucat, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.weight(1f)) {
            ChatView(
                messages = messages,
                modifier = Modifier.weight(1f)
            )
        }

        var inputText by remember { mutableStateOf("") }
        var dropdownExpanded by remember { mutableStateOf(false) }
        
        fun sendMessage() {
            if (inputText.isNotBlank()) {
                homeViewModel.sendRoomMessage(roomName, inputText)
                inputText = ""
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val remainingSeconds = homeViewModel.roomCountdowns[roomName.lowercase()]
            
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
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
                        "Bring IDs" to homeViewModel.multiLoginTemplate,
                        "Ready Check" to homeViewModel.readyCheckTemplate
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
                                homeViewModel.sendTemplate(roomName, value)
                            }
                        )
                    }
                    HorizontalDivider(color = NeonGreen.copy(alpha = 0.2f))
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = "KICK OFF", 
                                color = RedPucat, 
                                fontSize = 11.sp, 
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            ) 
                        },
                        onClick = {
                            dropdownExpanded = false
                            homeViewModel.kickoff(roomName)
                        }
                    )
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = "STARTER LEFT", 
                                color = Color(0xFFFFB300), 
                                fontSize = 11.sp, 
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ) 
                        },
                        onClick = {
                            dropdownExpanded = false
                            homeViewModel.starterLeave(roomName)
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = "SEND RESULT", 
                                color = if (pendingResult != null) Color(0xFFFFB300) else TextDim.copy(alpha = 0.5f), 
                                fontSize = 11.sp, 
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            ) 
                        },
                        onClick = {
                            dropdownExpanded = false
                            if (pendingResult != null) {
                                homeViewModel.sendManualMatchResult(roomName)
                            } else {
                                homeViewModel.showSnackbar("NO RESULT TO SEND YET")
                            }
                        }
                    )
                }
            }

            if (remainingSeconds != null) {
                val isTimeUp = remainingSeconds <= 0
                val min = remainingSeconds / 60
                val sec = remainingSeconds % 60
                val timeStr = if (isTimeUp) "DIS" else "%02d:%02d".format(min, sec)

                Row(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTimeUp) RedPucat else DarkGreen800)
                        .border(
                            1.dp, 
                            if (isTimeUp || remainingSeconds < 30) RedPucat else NeonGreen.copy(alpha = 0.5f), 
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { 
                            if (isTimeUp) showDisDialog = true 
                            else homeViewModel.stopManualCountdown(roomName) 
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        color = if (isTimeUp) Color.White else NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!isTimeUp) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "×",
                            color = RedPucat,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            XrefTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = "", 
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() })
            )
            
            XrefButton(
                text = if (homeViewModel.isUploadingImage) "..." else "IMG",
                onClick = { photoPickerLauncher.launch("image/*") },
                modifier = Modifier.width(42.dp),
                height = 42.dp,
                enabled = !homeViewModel.isUploadingImage,
                contentPadding = PaddingValues(horizontal = 0.dp),
                containerColor = DarkGreen800,
                contentColor = NeonGreen
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
    
    if (homeViewModel.teamSelectionDialogVisible) {
        TeamSelectionDialog(homeViewModel, roomName)
    }

    if (homeViewModel.matchSelectionDialogVisible) {
        MatchSelectionDialog(homeViewModel, roomName)
    }

    if (showDisDialog) {
        val scheduled = homeViewModel.scheduledMatches[roomName.lowercase()]
        val nameA = scheduled?.nameA ?: "TEAM A"
        val nameB = scheduled?.nameB ?: "TEAM B"

        AlertDialog(
            onDismissRequest = { showDisDialog = false },
            title = {
                Text(
                    text = "DECLARE DISQUALIFICATION",
                    color = NeonGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select the result for this match:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    XrefButton(
                        text = "$nameA WINS ($nameB DIS)",
                        onClick = {
                            homeViewModel.broadcastDisDecision(roomName, nameA, nameB, false)
                            showDisDialog = false
                        },
                        containerColor = DarkGreen800,
                        contentColor = NeonGreen,
                        height = 42.dp,
                        fontSize = 10.sp
                    )
                    
                    XrefButton(
                        text = "$nameB WINS ($nameA DIS)",
                        onClick = {
                            homeViewModel.broadcastDisDecision(roomName, nameB, nameA, false)
                            showDisDialog = false
                        },
                        containerColor = DarkGreen800,
                        contentColor = NeonGreen,
                        height = 42.dp,
                        fontSize = 10.sp
                    )
                    
                    XrefButton(
                        text = "BOTH TEAMS DIS",
                        onClick = {
                            homeViewModel.broadcastDisDecision(roomName, null, null, true)
                            showDisDialog = false
                        },
                        containerColor = RedPucatTrans,
                        contentColor = RedPucat,
                        borderColor = RedPucat,
                        height = 42.dp,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDisDialog = false }) {
                    Text("CANCEL", color = TextDim, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = DarkBackground,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
fun MatchSelectionDialog(viewModel: HomeViewModel, roomName: String) {
    val matches = viewModel.getAvailableMatchesFromBracket()

    AlertDialog(
        onDismissRequest = { viewModel.matchSelectionDialogVisible = false },
        title = {
            Text(
                text = "SELECT MATCH FOR ${roomName.uppercase()}",
                color = NeonGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (matches.isEmpty()) {
                    Text(
                        text = "NO AVAILABLE MATCHES IN BRACKET.\nENSURE SCORES ARE UPDATED.",
                        color = TextDim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(matches) { (match, phase) ->
                            XrefButton(
                                text = "${match.teamA} vs ${match.teamB} [$phase]",
                                onClick = {
                                    viewModel.matchSelectionDialogVisible = false
                                    viewModel.callMatchSummon(roomName, match.teamA, match.teamB, phase)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                height = 48.dp,
                                fontSize = 11.sp,
                                containerColor = DarkGreen800,
                                contentColor = NeonGreen
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.matchSelectionDialogVisible = false }) {
                Text("CANCEL", color = RedPucat)
            }
        },
        containerColor = DarkBackground,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun TeamSelectionDialog(viewModel: HomeViewModel, roomName: String) {
    var filterText by remember { mutableStateOf("") }
    val isTeamA = viewModel.currentSelectingTeamName == "TEAM A"
    val normalizedRoom = roomName.lowercase()
    
    val selectedList = remember(isTeamA, normalizedRoom) {
        if (isTeamA) {
            viewModel.selectedIdsA.getOrPut(normalizedRoom) { mutableStateListOf() }
        } else {
            viewModel.selectedIdsB.getOrPut(normalizedRoom) { mutableStateListOf() }
        }
    }
    
    val otherTeamList = if (isTeamA) {
        viewModel.selectedIdsB[normalizedRoom] ?: emptyList<String>()
    } else {
        viewModel.selectedIdsA[normalizedRoom] ?: emptyList<String>()
    }
    
    val allParticipants by viewModel.roomParticipants.collectAsState()
    val roomParticipantsList = (allParticipants[normalizedRoom] ?: emptyList())
        .filter { !otherTeamList.contains(it) }
    
    val filteredParticipants = remember(roomParticipantsList, filterText) {
        if (filterText.isBlank()) roomParticipantsList
        else roomParticipantsList.filter { it.contains(filterText, ignoreCase = true) }
    }

    LaunchedEffect(filterText) {
        if (filterText.length >= 3) {
            viewModel.autoSelectParticipants(filterText, roomName, isTeamA)
        }
    }

    AlertDialog(
        onDismissRequest = { viewModel.teamSelectionDialogVisible = false },
        title = {
            Text(
                text = "SELECT MULTI FOR ${viewModel.currentSelectingTeamName}",
                color = NeonGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                XrefTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    label = "Filter Name (min 3 chars for auto-select)"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECTED: ${selectedList.size} IDs",
                        color = if (selectedList.size == 10) NeonGreen else Color.Yellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    TextButton(
                        onClick = { selectedList.clear() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "CLEAR ALL",
                            color = RedPucat,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredParticipants) { username ->
                        val isSelected = selectedList.contains(username)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) NeonGreen.copy(alpha = 0.2f) else Color.Transparent)
                                .border(0.5.dp, if (isSelected) NeonGreen else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .clickable { viewModel.toggleParticipantSelection(username, roomName, isTeamA) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleParticipantSelection(username, roomName, isTeamA) },
                                colors = CheckboxDefaults.colors(checkedColor = NeonGreen, checkmarkColor = Color.Black)
                            )
                            Text(
                                text = username,
                                color = if (isSelected) NeonGreen else Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            XrefButton(
                text = "OK",
                onClick = { viewModel.teamSelectionDialogVisible = false },
                modifier = Modifier.width(80.dp),
                height = 36.dp,
                enabled = selectedList.size == 10
            )
        },
        containerColor = DarkBackground,
        shape = RoundedCornerShape(8.dp)
    )
}
