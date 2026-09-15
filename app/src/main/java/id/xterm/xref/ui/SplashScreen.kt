package id.xterm.xref.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.R
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.DarkGreen700
import id.xterm.xref.ui.theme.NeonGreen
import kotlinx.coroutines.delay

@Composable
fun SplashScreen() {
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        repeat(20) {
            progress = (it + 1) / 20f
            delay(120L)
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "splash-progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // Background Image filling the entire screen
        Image(
            painter = painterResource(id = R.drawable.klepon),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, // This makes it fill the screen
            alpha = 0.6f // Slight transparency so UI elements remain legible
        )

        // Overlay UI
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = "XREF TERMINAL",
                color = NeonGreen,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .width(240.dp)
                    .height(4.dp),
                color = NeonGreen,
                trackColor = DarkGreen700.copy(alpha = 0.5f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "INITIALIZING SYSTEM...",
                color = NeonGreen.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
