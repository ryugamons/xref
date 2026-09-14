package id.xterm.xref.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import id.xterm.xref.core.match.MatchManager
import id.xterm.xref.data.repository.WebSocketRepository
import id.xterm.xref.ui.components.XrefCard
import id.xterm.xref.ui.components.XrefTextField
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.NeonGreen

@Composable
fun BracketScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onLoadToDashboard: (teamA: List<String>, teamB: List<String>) -> Unit = { _, _ -> }
) {
    val registeredCount = homeViewModel.registeredParticipants.size
    val totalSlots = homeViewModel.bracketSize
    val participants = remember(registeredCount, totalSlots) {
        val list = homeViewModel.registeredParticipants.toMutableList()
        while (list.size < totalSlots) {
            list.add("EMPTY SLOT ${list.size + 1}")
        }
        list
    }

    val rounds = remember(participants, totalSlots) {
        val list = mutableListOf<RoundData>()
        
        if (totalSlots >= 16) {
            val r16Matches = (0 until 8).map { i ->
                MatchData(participants.getOrElse(i * 2) { "T${i * 2 + 1}" }, participants.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" })
            }
            list.add(RoundData("ROUND OF 16", r16Matches))
        }
        
        if (totalSlots >= 8) {
            val qfMatches = if (totalSlots > 8) {
                (0 until 4).map { MatchData("WINNER R16-${it * 2 + 1}", "WINNER R16-${it * 2 + 2}") }
            } else {
                (0 until 4).map { i ->
                    MatchData(participants.getOrElse(i * 2) { "T${i * 2 + 1}" }, participants.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" })
                }
            }
            list.add(RoundData("QUARTER-FINALS", qfMatches))
        }
        
        if (totalSlots >= 4) {
            val sfMatches = if (totalSlots > 4) {
                (0 until 2).map { MatchData("WINNER QF-${it * 2 + 1}", "WINNER QF-${it * 2 + 2}") }
            } else {
                (0 until 2).map { i ->
                    MatchData(participants.getOrElse(i * 2) { "T${i * 2 + 1}" }, participants.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" })
                }
            }
            list.add(RoundData("SEMI-FINALS", sfMatches))
        }
        
        val finalMatches = if (totalSlots > 2) {
            listOf(MatchData("WINNER SF1", "WINNER SF2"))
        } else {
            listOf(MatchData(participants.getOrElse(0) { "TEAM A" }, participants.getOrElse(1) { "TEAM B" }))
        }
        list.add(RoundData("FINAL", finalMatches))
        
        list
    }

    val webSocketRepository = remember { WebSocketRepository.getInstance() }
    val matchManager = remember { MatchManager.getInstance(webSocketRepository) }
    val activeSessions by matchManager.activeSessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "> TOURNAMENT BRACKET",
                color = NeonGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "TAP MATCH TO SUMMON",
                color = NeonGreen.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            rounds.forEach { round ->
                item {
                    Text(
                        text = "[ ${round.title} ]",
                        color = NeonGreen.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(round.matches) { match ->
                    val session = remember(activeSessions, match.teamA, match.teamB) {
                        homeViewModel.battleRooms.mapNotNull { room ->
                            matchManager.getSession(room)
                        }.firstOrNull { sess ->
                            val pA = sess.teamA.value.participants.map { it.id }
                            val pB = sess.teamB.value.participants.map { it.id }
                            pA.contains(match.teamA) && pB.contains(match.teamB)
                        }
                    }

                    val scoreAState = session?.teamA?.collectAsState()
                    val scoreBState = session?.teamB?.collectAsState()

                    val sessionScoreA = scoreAState?.value?.participants?.count { !it.hasVoted }?.toString() ?: "-"
                    val sessionScoreB = scoreBState?.value?.participants?.count { !it.hasVoted }?.toString() ?: "-"

                    var scoreAInput by remember(sessionScoreA) { mutableStateOf(sessionScoreA) }
                    var scoreBInput by remember(sessionScoreB) { mutableStateOf(sessionScoreB) }
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    val logs by session?.logs?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<String>()) }

                    XrefCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                XrefMatchRow(
                                    match = match,
                                    scoreA = scoreAInput,
                                    scoreB = scoreBInput,
                                    onScoreAChange = { scoreAInput = it },
                                    onScoreBChange = { scoreBInput = it },
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = NeonGreen,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            if (expanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "> MATCH HISTORY",
                                        color = NeonGreen.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Text(
                                        text = "[ CALL ]",
                                        color = NeonGreen,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            onLoadToDashboard(listOf(match.teamA), listOf(match.teamB))
                                        }
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkBackground.copy(alpha = 0.5f))
                                        .padding(8.dp)
                                ) {
                                    if (logs.isEmpty()) {
                                        Text(
                                            text = "NO ACTIVITY RECORDED",
                                            color = NeonGreen.copy(alpha = 0.3f),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        logs.take(5).forEach { log ->
                                            Text(
                                                text = log,
                                                color = NeonGreen.copy(alpha = 0.6f),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class MatchData(val teamA: String, val teamB: String)
data class RoundData(val title: String, val matches: List<MatchData>)

@Composable
fun XrefMatchRow(
    match: MatchData,
    scoreA: String,
    scoreB: String,
    onScoreAChange: (String) -> Unit,
    onScoreBChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = match.teamA,
            color = NeonGreen,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(8.dp))

        XrefTextField(
            value = scoreA,
            onValueChange = onScoreAChange,
            label = "",
            modifier = Modifier.width(50.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Text(
            text = "vs",
            color = NeonGreen.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        XrefTextField(
            value = scoreB,
            onValueChange = onScoreBChange,
            label = "",
            modifier = Modifier.width(50.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = match.teamB,
            color = NeonGreen,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}
