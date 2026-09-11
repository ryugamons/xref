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
import java.util.Locale

class DashboardViewModel : ViewModel() {
    private val webSocketRepository = WebSocketRepository.getInstance()
    val matchManager = MatchManager.getInstance(webSocketRepository)

    fun startTestMatch() {
        val teamA = (1..10).map { "A_ID_$it" }
        val teamB = (1..10).map { "B_ID_$it" }
        matchManager.startMatch(teamA, teamB)
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val matchState by viewModel.matchManager.state.collectAsState()
    val teamA by viewModel.matchManager.teamA.collectAsState()
    val teamB by viewModel.matchManager.teamB.collectAsState()
    val logs by viewModel.matchManager.logs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Room Info
        if (homeViewModel.roomId.isNotEmpty()) {
            Text(
                text = "ROOM: ${homeViewModel.roomId.uppercase()}",
                color = NeonGreen.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Timer Section
        TimerSection(matchState)

        Spacer(modifier = Modifier.height(24.dp))

        // Teams Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TeamListColumn("Team A", teamA.participants, Modifier.weight(1f))
            TeamListColumn("Team B", teamB.participants, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logs Section
        LogSection(logs, Modifier.weight(0.6f))
        
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


