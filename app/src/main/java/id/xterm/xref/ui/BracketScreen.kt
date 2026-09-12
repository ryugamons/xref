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
    val participants = homeViewModel.participants

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
        if (participants.size > 8) {
            // Round of 16
            RoundColumn(
                title = "ROUND OF 16",
                matches = listOf(
                    MatchData(participants.getOrElse(0) { "TEAM 1" }, participants.getOrElse(1) { "TEAM 2" }),
                    MatchData(participants.getOrElse(2) { "TEAM 3" }, participants.getOrElse(3) { "TEAM 4" }),
                    MatchData(participants.getOrElse(4) { "TEAM 5" }, participants.getOrElse(5) { "TEAM 6" }),
                    MatchData(participants.getOrElse(6) { "TEAM 7" }, participants.getOrElse(7) { "TEAM 8" }),
                    MatchData(participants.getOrElse(8) { "TEAM 9" }, participants.getOrElse(9) { "TEAM 10" }),
                    MatchData(participants.getOrElse(10) { "TEAM 11" }, participants.getOrElse(11) { "TEAM 12" }),
                    MatchData(participants.getOrElse(12) { "TEAM 13" }, participants.getOrElse(13) { "TEAM 14" }),
                    MatchData(participants.getOrElse(14) { "TEAM 15" }, participants.getOrElse(15) { "TEAM 16" })
                ),
                onMatchClick = onLoadToDashboard
            )

            // Connections R16 -> QF
            BracketLines(count = 4, height = 480.dp)
        }

        // Quarter-finals
        RoundColumn(
            title = "QUARTER-FINALS",
            matches = if (participants.size > 8) {
                listOf(
                    MatchData("WINNER R16-1", "WINNER R16-2"),
                    MatchData("WINNER R16-3", "WINNER R16-4"),
                    MatchData("WINNER R16-5", "WINNER R16-6"),
                    MatchData("WINNER R16-7", "WINNER R16-8")
                )
            } else {
                listOf(
                    MatchData(participants.getOrElse(0) { "TEAM ALPHA" }, participants.getOrElse(1) { "TEAM BETA" }),
                    MatchData(participants.getOrElse(2) { "TEAM GAMMA" }, participants.getOrElse(3) { "TEAM DELTA" }),
                    MatchData(participants.getOrElse(4) { "TEAM EPSILON" }, participants.getOrElse(5) { "TEAM ZETA" }),
                    MatchData(participants.getOrElse(6) { "TEAM ETA" }, participants.getOrElse(7) { "TEAM THETA" })
                )
            },
            modifier = Modifier.align(Alignment.CenterVertically),
            onMatchClick = onLoadToDashboard
        )

        // Connections QF -> SF
        BracketLines(count = 2, height = 480.dp)

        // Semi-finals
        RoundColumn(
            title = "SEMI-FINALS",
            matches = listOf(
                MatchData("WINNER QF1", "WINNER QF2"),
                MatchData("WINNER QF3", "WINNER QF4")
            ),
            modifier = Modifier.align(Alignment.CenterVertically),
            onMatchClick = onLoadToDashboard
        )

        // Connections SF -> F
        BracketLines(count = 1, height = 240.dp)

        // Final
        RoundColumn(
            title = "GRAND FINAL",
            matches = listOf(
                MatchData("WINNER SF1", "WINNER SF2")
            ),
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
        
        // Simple bracket line implementation
        // This draws a bracket for each pair of matches
        val pairHeight = h / count
        for (i in 0 until count) {
            val yTop = (i * pairHeight) + (pairHeight * 0.25f)
            val yBottom = (i * pairHeight) + (pairHeight * 0.75f)
            val yMid = (i * pairHeight) + (pairHeight * 0.5f)
            
            // Horizontal from start to mid
            drawLine(color, Offset(0f, yTop), Offset(w / 2, yTop), strokeWidth)
            drawLine(color, Offset(0f, yBottom), Offset(w / 2, yBottom), strokeWidth)
            
            // Vertical connection
            drawLine(color, Offset(w / 2, yTop), Offset(w / 2, yBottom), strokeWidth)
            
            // Horizontal to next round
            drawLine(color, Offset(w / 2, yMid), Offset(w, yMid), strokeWidth)
        }
    }
}

data class MatchData(val teamA: String, val teamB: String)
