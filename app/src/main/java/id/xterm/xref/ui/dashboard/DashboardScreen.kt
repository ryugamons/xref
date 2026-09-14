package id.xterm.xref.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import id.xterm.xref.core.match.MatchManager
import id.xterm.xref.core.match.MatchParticipant
import id.xterm.xref.core.match.MatchState
import id.xterm.xref.data.repository.WebSocketRepository
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.NeonGreen
import kotlinx.coroutines.flow.MutableStateFlow
import id.xterm.xref.core.match.MatchSide
import java.util.Locale
import androidx.compose.foundation.clickable
import id.xterm.xref.ui.components.XrefCard
import id.xterm.xref.ui.components.XrefTextField
import id.xterm.xref.ui.components.XrefButton

class DashboardViewModel : ViewModel() {
    private val webSocketRepository = WebSocketRepository.getInstance()
    val matchManager = MatchManager.getInstance(webSocketRepository)
    
    var selectedRoom by mutableStateOf<String?>(null)

    var matchCallTemplate by mutableStateOf("[BROADCAST] Match starting: {teamA} vs {teamB}. Enter room: {room}")
    var bringMultiIdsTemplate by mutableStateOf("[REFEREE] Bring your 10 Multi-IDs into the room now!")
    var readyCheckTemplate by mutableStateOf("[REFEREE] Are you ready? Reply 'rd' to confirm!")
    var kickoffWarningTemplate by mutableStateOf("[REFEREE] KICKOFF STARTED! Do not vote/kick for 60 seconds!")

    fun startMatch(room: String, teamA: List<String>, teamB: List<String>) {
        matchManager.startMatch(room, teamA, teamB)
        selectedRoom = room
    }

    fun startTestMatch() {
        val teamA = (1..10).map { "A_ID_$it" }
        val teamB = (1..10).map { "B_ID_$it" }
        startMatch("war 1", teamA, teamB)
    }

    fun sendTemplate(templateText: String) {
        val room = selectedRoom ?: return
        val session = matchManager.getSession(room)
        val teamAName = session?.teamA?.value?.name ?: "Team A"
        val teamBName = session?.teamB?.value?.name ?: "Team B"
        
        val processedMessage = templateText
            .replace("{room}", room.uppercase())
            .replace("{teamA}", teamAName)
            .replace("{teamB}", teamBName)
            
        webSocketRepository.sendMessage(room, processedMessage, "REFEREE")
    }

    fun saveTemplates(matchCall: String, bringMultiIds: String, readyCheck: String, kickoffWarning: String) {
        matchCallTemplate = matchCall
        bringMultiIdsTemplate = bringMultiIds
        readyCheckTemplate = readyCheck
        kickoffWarningTemplate = kickoffWarning
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val activeSessions by viewModel.matchManager.activeSessions.collectAsState()
    
    // Automatically select the first session if none selected
    LaunchedEffect(activeSessions) {
        if (viewModel.selectedRoom == null && activeSessions.isNotEmpty()) {
            viewModel.selectedRoom = activeSessions[0]
        }
    }

    val currentRoom = viewModel.selectedRoom
    val session = currentRoom?.let { viewModel.matchManager.getSession(it) }

    val matchState by (session?.state ?: MutableStateFlow(MatchState.Idle)).collectAsState()
    val teamA by (session?.teamA ?: MutableStateFlow(MatchSide("Team A", emptyList()))).collectAsState()
    val teamB by (session?.teamB ?: MutableStateFlow(MatchSide("Team B", emptyList()))).collectAsState()
    val logs by (session?.logs ?: MutableStateFlow(emptyList<String>())).collectAsState()

    var isTemplatesExpanded by remember { mutableStateOf(false) }

    var localMatchCallTemplate by remember(viewModel.matchCallTemplate) { mutableStateOf(viewModel.matchCallTemplate) }
    var localBringMultiIdsTemplate by remember(viewModel.bringMultiIdsTemplate) { mutableStateOf(viewModel.bringMultiIdsTemplate) }
    var localReadyCheckTemplate by remember(viewModel.readyCheckTemplate) { mutableStateOf(viewModel.readyCheckTemplate) }
    var localKickoffWarningTemplate by remember(viewModel.kickoffWarningTemplate) { mutableStateOf(viewModel.kickoffWarningTemplate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Room Selector for Simultaneous Battles
        if (activeSessions.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = activeSessions.indexOf(currentRoom).coerceAtLeast(0),
                containerColor = Color.Transparent,
                contentColor = NeonGreen,
                edgePadding = 0.dp,
                divider = {}
            ) {
                activeSessions.forEach { room ->
                    Tab(
                        selected = currentRoom == room,
                        onClick = { viewModel.selectedRoom = room },
                        text = { Text(room.uppercase(), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Room Info
        Text(
            text = "ARENA: ${currentRoom?.uppercase() ?: "NONE"}",
            color = NeonGreen.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Timer Section
        TimerSection(matchState)

        Spacer(modifier = Modifier.height(24.dp))

        // Teams Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isTemplatesExpanded) 0.5f else 1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TeamListColumn("Team A", teamA.participants, Modifier.weight(1f))
            TeamListColumn("Team B", teamB.participants, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logs Section
        LogSection(logs, Modifier.weight(if (isTemplatesExpanded) 0.3f else 0.6f))

        // Referee Text Templates Configuration Card
        XrefCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            title = "REFEREE TEXT TEMPLATES"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isTemplatesExpanded = !isTemplatesExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTemplatesExpanded) "COLLAPSE CONFIG" else "EXPAND CONFIG",
                    color = NeonGreen,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isTemplatesExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                val templates = listOf(
                    Triple("Match Call", localMatchCallTemplate) { newValue: String -> localMatchCallTemplate = newValue },
                    Triple("Bring Multi-IDs", localBringMultiIdsTemplate) { newValue: String -> localBringMultiIdsTemplate = newValue },
                    Triple("Ready Check", localReadyCheckTemplate) { newValue: String -> localReadyCheckTemplate = newValue },
                    Triple("Kickoff Warning", localKickoffWarningTemplate) { newValue: String -> localKickoffWarningTemplate = newValue }
                )
                templates.forEach { (label, value, onValueChangeBlock) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        XrefTextField(
                            value = value,
                            onValueChange = onValueChangeBlock,
                            label = label,
                            modifier = Modifier.weight(1f)
                        )
                        XrefButton(
                            text = "SEND",
                            onClick = { viewModel.sendTemplate(value) },
                            enabled = currentRoom != null,
                            modifier = Modifier.width(75.dp),
                            height = 42.dp,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                XrefButton(
                    text = "SAVE",
                    onClick = {
                        viewModel.saveTemplates(
                            matchCall = localMatchCallTemplate,
                            bringMultiIds = localBringMultiIdsTemplate,
                            readyCheck = localReadyCheckTemplate,
                            kickoffWarning = localKickoffWarningTemplate
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 42.dp
                )
            }
        }
        
        // Actions
        Button(
            onClick = { viewModel.startTestMatch() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DarkBackground),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("START MATCH (TEST)", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TimerSection(state: MatchState) {
    val remaining = if (state is MatchState.Running) state.timeRemainingMillis else 0L
    val seconds = remaining / 1000f
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "3.0s TIMEOUT",
            color = NeonGreen.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = String.format(Locale.US, "%.1f", seconds),
            color = NeonGreen,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.shadow(elevation = 8.dp, ambientColor = NeonGreen, spotColor = NeonGreen)
        )
        Text(
            text = when(state) {
                is MatchState.Idle -> "READY"
                is MatchState.Running -> "MATCH IN PROGRESS"
                is MatchState.Ended -> "MATCH ENDED"
                is MatchState.Result -> "RESULT: ${state.winner}"
            },
            color = NeonGreen,
            fontSize = 14.sp
        )
    }
}

@Composable
fun TeamListColumn(title: String, participants: List<MatchParticipant>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(
            text = title,
            color = NeonGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        participants.forEach { p ->
            Text(
                text = "${if (p.hasVoted) "[X]" else "[ ]"} ${p.id}",
                color = if (p.hasVoted) NeonGreen else NeonGreen.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun LogSection(logs: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "MATCH LOGS",
            color = NeonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                Text(
                    text = "> $log",
                    color = NeonGreen,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}


