package id.xterm.xref.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import id.xterm.xref.ui.components.XrefButton
import id.xterm.xref.ui.components.XrefCard
import id.xterm.xref.ui.components.XrefTextField
import id.xterm.xref.ui.home.HomeViewModel
import id.xterm.xref.ui.home.MatchData
import id.xterm.xref.ui.home.RoundData
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.DarkGreen800
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.TextDim

@Composable
fun BracketScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onLoadToDashboard: (teamA: List<String>, teamB: List<String>) -> Unit = { _, _ -> }
) {
    val totalSlots = homeViewModel.bracketSize
    val participants = remember(homeViewModel.registeredParticipants.toList(), totalSlots) {
        val list = homeViewModel.registeredParticipants.toMutableList()
        while (list.size < totalSlots) {
            list.add("EMPTY SLOT ${list.size + 1}")
        }
        list
    }

    var bracketVersion by remember { mutableStateOf(0) }

    val rounds = remember(participants, totalSlots, bracketVersion) {
        val list = mutableListOf<RoundData>()
        val bracketScores = homeViewModel.bracketScores
        
        var currentNames = participants.toList()
        
        val allRoundNames = listOf("ROUND OF 64", "ROUND OF 32", "ROUND OF 16", "QUARTER-FINALS", "SEMI-FINALS", "FINAL")
        
        // Determine starting round index based on totalSlots
        val startRoundIdx = when (totalSlots) {
            64 -> 0
            32 -> 1
            16 -> 2
            8 -> 3
            4 -> 4
            2 -> 5
            else -> 5
        }
        
        val roundNames = allRoundNames.drop(startRoundIdx)
        
        var roundSize = totalSlots / 2
        var roundIdx = 0
        
        while (roundSize >= 1 && roundIdx < roundNames.size) {
            val title = roundNames[roundIdx]
            val matches = (0 until roundSize).map { i ->
                MatchData(
                    currentNames.getOrElse(i * 2) { "T${i * 2 + 1}" },
                    currentNames.getOrElse(i * 2 + 1) { "T${i * 2 + 2}" }
                )
            }
            val roundData = RoundData(title, matches)
            list.add(roundData)
            
            // Calculate winners for next round
            currentNames = matches.indices.map { i ->
                val score = bracketScores["${title}_$i"]
                val sA = score?.first?.toIntOrNull() ?: -1
                val sB = score?.second?.toIntOrNull() ?: -1
                
                if (sA > sB) matches[i].teamA 
                else if (sB > sA) matches[i].teamB 
                else "WINNER ${title}-${i + 1}"
            }
            
            roundSize /= 2
            roundIdx++
        }
        
        list
    }

    val webSocketRepository = remember { WebSocketRepository.getInstance() }
    val matchManager = remember { MatchManager.getInstance(webSocketRepository) }
    val activeSessions by matchManager.activeSessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "> TOURNAMENT BRACKET",
                color = NeonGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                XrefButton(
                    text = "UPDATE",
                    onClick = { bracketVersion++ },
                    modifier = Modifier.width(62.dp),
                    height = 28.dp,
                    fontSize = 8.sp,
                    contentPadding = PaddingValues(0.dp)
                )
                XrefButton(
                    text = "BROADCAST",
                    onClick = { homeViewModel.broadcastManualBracket(rounds) },
                    modifier = Modifier.width(82.dp),
                    height = 28.dp,
                    fontSize = 8.sp,
                    contentPadding = PaddingValues(0.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rounds.forEach { round ->
                item {
                    Text(
                        text = "[ ${round.title} ]",
                        color = NeonGreen.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                itemsIndexed(round.matches) { matchIndex, match ->
                    val matchKey = "${round.title}_$matchIndex"
                    val session = remember(activeSessions, match.teamA, match.teamB) {
                        homeViewModel.battleRooms.mapNotNull { room ->
                            matchManager.getSession(room)
                        }.firstOrNull { sess ->
                            sess.teamA.value.name == match.teamA && sess.teamB.value.name == match.teamB
                        }
                    }

                    val scoreAState = session?.teamA?.collectAsState()
                    val scoreBState = session?.teamB?.collectAsState()

                    val sessionScoreA = scoreAState?.value?.participants?.count { !it.isKicked }?.toString() ?: "-"
                    val sessionScoreB = scoreBState?.value?.participants?.count { !it.isKicked }?.toString() ?: "-"

                    // Load from VM if manual score exists, otherwise use session score
                    val savedScore = homeViewModel.bracketScores[matchKey]
                    var scoreAInput by remember(sessionScoreA, savedScore) { 
                        mutableStateOf(savedScore?.first ?: sessionScoreA) 
                    }
                    var scoreBInput by remember(sessionScoreB, savedScore) { 
                        mutableStateOf(savedScore?.second ?: sessionScoreB) 
                    }
                    var expanded by rememberSaveable { mutableStateOf(false) }

                    XrefCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Match Info & Scores (Clickable area only for expanding)
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { expanded = !expanded },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    XrefMatchRow(
                                        match = match,
                                        scoreA = scoreAInput,
                                        scoreB = scoreBInput,
                                        onScoreAChange = { 
                                            scoreAInput = it
                                            homeViewModel.updateBracketScore(round.title, matchIndex, it, scoreBInput)
                                        },
                                        onScoreBChange = { 
                                            scoreBInput = it
                                            homeViewModel.updateBracketScore(round.title, matchIndex, scoreAInput, it)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand",
                                        tint = NeonGreen,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                // CALL/CANCEL Button
                                val isThisActiveSummon = homeViewModel.activeSummonTeams?.first == match.teamA && homeViewModel.activeSummonTeams?.second == match.teamB
                                val hasManualScore = scoreAInput.isNotEmpty() && scoreAInput != "-" && 
                                                   scoreBInput.isNotEmpty() && scoreBInput != "-"

                                XrefButton(
                                    text = if (isThisActiveSummon) "CANCEL" else "CALL",
                                    onClick = {
                                        if (isThisActiveSummon) {
                                            homeViewModel.cancelSummon()
                                        } else if (!homeViewModel.isSummoning) {
                                            onLoadToDashboard(listOf(match.teamA, round.title), listOf(match.teamB))
                                        }
                                    },
                                    modifier = Modifier.width(64.dp).padding(start = 4.dp),
                                    height = 32.dp,
                                    fontSize = 10.sp,
                                    enabled = isThisActiveSummon || (!homeViewModel.isSummoning && !hasManualScore),
                                    containerColor = when {
                                        isThisActiveSummon -> Color(0xFF8B2525)
                                        hasManualScore -> DarkGreen800
                                        else -> NeonGreen
                                    },
                                    contentColor = if (isThisActiveSummon) Color.White else if (hasManualScore) TextDim else Color.Black,
                                    contentPadding = PaddingValues(0.dp)
                                )
                            }

                            if (expanded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = "> MATCH HISTORY",
                                    color = NeonGreen.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val kickLogs by session?.kickLogs?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<id.xterm.xref.core.match.KickLog>()) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkBackground.copy(alpha = 0.5f))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Team A Column
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${session?.teamA?.value?.name?.uppercase() ?: "TEAM A"} LOGS", color = NeonGreen.copy(alpha = 0.4f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        HorizontalDivider(color = NeonGreen.copy(alpha = 0.1f), thickness = 0.5.dp)
                                        kickLogs.filter { it.team == "A" }.forEach { log ->
                                            Text(
                                                text = "[${log.timeMs}ms] ${log.username.take(8).let { if (it.length < log.username.length) it + ".." else it }} ${if(log.action == "FAILED") "(!)" else ""}",
                                                color = if(log.action == "FAILED") Color.Yellow.copy(alpha = 0.7f) else NeonGreen.copy(alpha = 0.7f),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(NeonGreen.copy(alpha = 0.1f)))

                                    // Team B Column
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Text("${session?.teamB?.value?.name?.uppercase() ?: "TEAM B"} LOGS", color = NeonGreen.copy(alpha = 0.4f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        HorizontalDivider(color = NeonGreen.copy(alpha = 0.1f), thickness = 0.5.dp)
                                        kickLogs.filter { it.team == "B" }.forEach { log ->
                                            Text(
                                                text = "${if(log.action == "FAILED") "(!)" else ""} ${log.username.take(8).let { if (it.length < log.username.length) it + ".." else it }} [${log.timeMs}ms]",
                                                color = if(log.action == "FAILED") Color.Yellow.copy(alpha = 0.7f) else NeonGreen.copy(alpha = 0.7f),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(vertical = 1.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }
                                
                                if (kickLogs.isEmpty()) {
                                    Text(
                                        text = "NO KICK ACTIVITY",
                                        color = NeonGreen.copy(alpha = 0.3f),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        textAlign = TextAlign.Center
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
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(4.dp))

        XrefTextField(
            value = scoreA,
            onValueChange = onScoreAChange,
            label = "",
            modifier = Modifier.width(36.dp),
            horizontalPadding = 4.dp,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Text(
            text = "vs",
            color = NeonGreen.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        XrefTextField(
            value = scoreB,
            onValueChange = onScoreBChange,
            label = "",
            modifier = Modifier.width(36.dp),
            horizontalPadding = 4.dp,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = match.teamB,
            color = NeonGreen,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}
