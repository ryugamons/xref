package id.xterm.xref.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.core.match.MatchManager
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.NeonGreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RoomScreen(matchManager: MatchManager) {
    val logs by matchManager.logs.collectAsState()
    RoomContent(logs)
}

@Composable
fun RoomContent(logs: List<String>) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Text(
            text = "> TERMINAL OUTPUT",
            color = NeonGreen,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, NeonGreen, RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { log ->
                    TerminalLogItem(log)
                }
                
                item {
                    TerminalCursor()
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "STATUS: MONITORING ACTIVE",
            color = NeonGreen.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TerminalLogItem(log: String) {
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) }
    
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "[$time]",
            color = NeonGreen.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = log,
            color = when {
                log.contains("Kicked", ignoreCase = true) -> NeonGreen
                log.contains("Error", ignoreCase = true) -> Color.Red
                log.contains("Warning", ignoreCase = true) -> Color.Yellow
                else -> Color.White
            },
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (log.contains("Kicked")) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun TerminalCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = ">",
            color = NeonGreen,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(14.dp)
                .background(NeonGreen.copy(alpha = alpha))
        )
    }
}


