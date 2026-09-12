package id.xterm.xref.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.ui.theme.DarkGreen700
import id.xterm.xref.ui.theme.DarkGreen800
import id.xterm.xref.ui.theme.NeonGreen
import id.xterm.xref.ui.theme.TextDim

@Composable
fun StatusCard(
    id: String,
    status: String,
    credits: String,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkGreen800)
            .border(1.dp, DarkGreen700, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (isConnected) NeonGreen else TextDim.copy(alpha = 0.4f))
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // ID and Status text
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (id.isEmpty()) "---" else id,
                color = TextDim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                color = if (isConnected) NeonGreen else TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        // Credits section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = "⎔ ", // Coin-like symbol
                color = Color(0xFFFFB300),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = credits,
                color = Color(0xFFFFB300),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Connection Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isConnected) NeonGreen.copy(alpha = 0.1f) else Color.Transparent)
                .border(
                    1.dp,
                    if (isConnected) NeonGreen.copy(alpha = 0.3f) else TextDim.copy(alpha = 0.3f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isConnected) "connected" else "disconnected",
                color = if (isConnected) NeonGreen else TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
