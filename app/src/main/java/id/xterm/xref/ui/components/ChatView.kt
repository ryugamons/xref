package id.xterm.xref.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import id.xterm.xref.R
import id.xterm.xref.core.websocket.ChatMessage
import id.xterm.xref.core.websocket.MessageType
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.TextLight
import kotlinx.coroutines.delay

@Composable
fun ChatView(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(20L)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .background(Color.Black)
    ) {
        Image(
            painter = painterResource(id = R.drawable.klepon),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.25f),
            contentScale = ContentScale.Crop
        )
        
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages) { message ->
                ChatItem(
                    message = message,
                    onImageClick = { url -> fullScreenImageUrl = url }
                )
            }
        }
    }

    // Full Screen Image Preview Modal
    fullScreenImageUrl?.let { imageUrl ->
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { fullScreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Full Image View",
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = NeonGreen,
                                strokeWidth = 3.dp
                            )
                        }
                    },
                    error = {
                        Text(
                            text = "[ Failed to load full image ]",
                            color = Color.Red,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.85f)
                )

                Text(
                    text = "[ Tap anywhere to close ]",
                    color = NeonGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatItem(
    message: ChatMessage,
    onImageClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        when (message.eventType) {
            "room.joined", "room.participant.added", "room.participant.removed" -> {
                val displayName = if (message.username.isNotEmpty()) "${message.username} " else ""
                
                Text(
                    text = "$displayName${message.text}",
                    color = NeonGreen.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light
                )
            }
            "room.message.received" -> {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${message.username}: ",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (message.text.isNotBlank() && (message.mediaUrl.isNullOrEmpty() || message.text != "sent an image")) {
                            Text(
                                text = message.text,
                                color = if (message.type == MessageType.ACTION) Color(0xFFFFB300) else TextLight,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else if (!message.mediaUrl.isNullOrEmpty() && message.text == "sent an image") {
                            Text(
                                text = "sent an image:",
                                color = TextLight.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    message.mediaUrl?.let { url ->
                        if (url.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SubcomposeAsyncImage(
                                model = url,
                                contentDescription = "Image Preview",
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp, 90.dp)
                                            .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = NeonGreen,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                },
                                error = {
                                    Text(
                                        text = "[Failed to load image]",
                                        color = Color.Red,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .widthIn(max = 180.dp)
                                    .heightIn(max = 120.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color.Yellow, RoundedCornerShape(6.dp))
                                    .clickable { onImageClick(url) }
                            )
                        }
                    }
                }
            }
            else -> {
                Column {
                    Text(
                        text = "${message.username}: ${message.text}",
                        color = TextLight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    message.mediaUrl?.let { url ->
                        if (url.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SubcomposeAsyncImage(
                                model = url,
                                contentDescription = "Image Preview",
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp, 90.dp)
                                            .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = NeonGreen,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                },
                                error = {
                                    Text(
                                        text = "[Failed to load image]",
                                        color = Color.Red,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .widthIn(max = 180.dp)
                                    .heightIn(max = 120.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color.Yellow, RoundedCornerShape(6.dp))
                                    .clickable { onImageClick(url) }
                            )
                        }
                    }
                }
            }
        }
    }
}
