package id.xterm.xref.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.core.websocket.ChatMessage
import id.xterm.xref.core.websocket.MessageType
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.TextLight
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatView(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages) { message ->
                ChatItem(message)
            }
        }
    }
}

@Composable
private fun ChatItem(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "[${message.time.takeLast(9).replace("Z", "")}]",
            color = NeonGreen.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 6.dp, top = 2.dp)
        )
        
        when (message.eventType) {
            "room.joined", "room.left" -> {
                Text(
                    text = "${message.room}: ${message.username} ${message.text}",
                    color = NeonGreen.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light
                )
            }
            "room.message.received" -> {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${message.username}: ",
                        color = NeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = message.text,
                        color = if (message.type == MessageType.ACTION) Color(0xFFFFB300) else TextLight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            else -> {
                // Fallback for other message types
                Text(
                    text = "${message.username}: ${message.text}",
                    color = TextLight,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
