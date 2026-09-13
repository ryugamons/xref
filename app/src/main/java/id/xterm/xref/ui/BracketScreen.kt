package id.xterm.xref.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.NeonGreen

@Composable
fun BracketScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onLoadToDashboard: (teamA: List<String>, teamB: List<String>) -> Unit = { _, _ -> }
) {
    val scrollState = rememberScrollState()
    
    // Fill remaining slots with dummy names to complete the bracket
    val registeredCount = homeViewModel.registeredParticipants.size
    val totalSlots = homeViewModel.bracketSize
    val participants = remember(registeredCount, totalSlots) {
        val list = homeViewModel.registeredParticipants.toMutableList()
        while (list.size < totalSlots) {
            list.add("EMPTY SLOT ${list.size + 1}")
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
    ) {
        Text(
            text = "> TOURNAMENT BRACKET",
            color = NeonGreen,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            TournamentTree(participants, onLoadToDashboard)
        }
    }
}

@Composable
fun TournamentTree(participants: List<String>, onLoadToDashboard: (List<String>, List<String>) -> Unit) {
    Row(
        modifier = Modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        val size = participants.size

        if (size >= 16) {
            // Round of 16
            RoundColumn(
                title = "ROUND OF 16",
                matches = (0 until 8).map { i ->
                    MatchData(participants.getOrElse(i * 2) { "T${i * 2 + 1}" }, participants.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" })
                },
                onMatchClick = onLoadToDashboard
            )
            BracketLines(count = 4, height = 480.dp)
        }

        if (size >= 8) {
            // Quarter-finals
            RoundColumn(
                title = "QUARTER-FINALS",
                matches = if (size > 8) {
                    (0 until 4).map { MatchData("WINNER R16-${it * 2 + 1}", "WINNER R16-${it * 2 + 2}") }
                } else {
                    (0 until 4).map { i ->
                        MatchData(participants.getOrElse(i * 2) { "T${i * 2 + 1}" }, participants.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" })
                    }
                },
                modifier = Modifier.align(Alignment.CenterVertically),
                onMatchClick = onLoadToDashboard
            )
            BracketLines(count = 2, height = 480.dp)
        }

        if (size >= 4) {
            // Semi-finals
            RoundColumn(
                title = "SEMI-FINALS",
                matches = if (size > 4) {
                    (0 until 2).map { MatchData("WINNER QF-${it * 2 + 1}", "WINNER QF-${it * 2 + 2}") }
                } else {
                    (0 until 2).map { i ->
                        MatchData(participants.getOrElse(i * 2) { "T${i * 2 + 1}" }, participants.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" })
                    }
                },
                modifier = Modifier.align(Alignment.CenterVertically),
                onMatchClick = onLoadToDashboard
            )
            BracketLines(count = 1, height = 240.dp)
        }

        // Final (Always show for 2 or more)
        RoundColumn(
            title = "GRAND FINAL",
            matches = if (size > 2) {
                listOf(MatchData("WINNER SF1", "WINNER SF2"))
            } else {
                listOf(MatchData(participants.getOrElse(0) { "TEAM A" }, participants.getOrElse(1) { "TEAM B" }))
            },
            modifier = Modifier.align(Alignment.CenterVertically),
            onMatchClick = onLoadToDashboard
        )
    }
}

@Composable
fun RoundColumn(
    title: String,
    matches: List<MatchData>,
    modifier: Modifier = Modifier,
    onMatchClick: (List<String>, List<String>) -> Unit
) {
    Column(
        modifier = modifier.width(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text(
            text = "[ $title ]",
            color = NeonGreen.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        matches.forEach { match ->
            MatchBox(match) {
                onMatchClick(listOf(match.teamA), listOf(match.teamB))
            }
        }
    }
}

@Composable
fun MatchBox(match: MatchData, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonGreen, RoundedCornerShape(2.dp))
            .background(Color.Black)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        TeamRow(name = match.teamA, isWinner = false)
        HorizontalDivider(
            color = NeonGreen.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        TeamRow(name = match.teamB, isWinner = false)
    }
}

@Composable
fun TeamRow(name: String, isWinner: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (isWinner) NeonGreen else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        if (isWinner) {
            Text(text = "W", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BracketLines(count: Int, height: Dp) {
    Canvas(modifier = Modifier.width(30.dp).height(height)) {
        val strokeWidth = 1.dp.toPx()
        val color = NeonGreen.copy(alpha = 0.5f)
        val w = size.width
        val h = size.height
        
        val pairHeight = h / count
        for (i in 0 until count) {
            val yTop = (i * pairHeight) + (pairHeight * 0.25f)
            val yBottom = (i * pairHeight) + (pairHeight * 0.75f)
            val yMid = (i * pairHeight) + (pairHeight * 0.5f)
            
            drawLine(color, Offset(0f, yTop), Offset(w / 2, yTop), strokeWidth)
            drawLine(color, Offset(0f, yBottom), Offset(w / 2, yBottom), strokeWidth)
            drawLine(color, Offset(w / 2, yTop), Offset(w / 2, yBottom), strokeWidth)
            drawLine(color, Offset(w / 2, yMid), Offset(w, yMid), strokeWidth)
        }
    }
}

data class MatchData(val teamA: String, val teamB: String)
