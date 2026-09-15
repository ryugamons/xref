package id.xterm.xref.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.core.match.MatchManager
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.NeonGreen
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

@Composable
fun StatisticsScreen(matchManager: MatchManager) {
    val activeSessions by matchManager.activeSessions.collectAsState()
    
    if (activeSessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "NO ACTIVE MATCHES", color = NeonGreen, fontFamily = FontFamily.Monospace)
        }
    } else {
        val session = matchManager.getSession(activeSessions[0])
        if (session != null) {
            val kickCountMap by session.kickCountMap.collectAsState()
            val logs by session.logs.collectAsState(initial = emptyList())
            
            StatisticsContent(0f, kickCountMap, emptyList())
        }
    }
}

@Composable
fun StatisticsContent(
    kps: Float,
    kickCountMap: Map<String, Int>,
    kickHistory: List<Long>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "> LIVE STATISTICS",
                color = NeonGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // KPS Meter
                Box(modifier = Modifier.weight(1f)) {
                    KpsMeter(kps)
                }
                
                // Stats Summary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .border(1.dp, NeonGreen, RoundedCornerShape(2.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    StatValue("TOTAL KICKS", kickCountMap.values.sum().toString())
                    StatValue("ACTIVE IDS", kickCountMap.size.toString())
                    StatValue("PEAK KPS", "0.0") 
                }
            }
        }

        item {
            Text(
                text = "> ID ACTIVITY",
                color = NeonGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        val sortedActivity = kickCountMap.toList().sortedByDescending { it.second }
        if (sortedActivity.isEmpty()) {
            item {
                Text(
                    text = "NO ACTIVITY DATA",
                    color = NeonGreen.copy(alpha = 0.3f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(sortedActivity) { (id, count) ->
                IdActivityItem(id, count)
            }
        }

        item {
            Text(
                text = "> ROOM ACTIVITY (60S)",
                color = NeonGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        
        item {
            RoomActivityGraph(kickHistory)
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatValue(label: String, value: String) {
    Column {
        Text(text = label, color = NeonGreen.copy(alpha = 0.5f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun KpsMeter(kps: Float) {
    val animatedKps by animateFloatAsState(
        targetValue = kps,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "KPS"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(1.dp, NeonGreen, RoundedCornerShape(2.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * 0.85f)
            val radius = size.height * 0.7f
            
            drawArc(
                color = NeonGreen.copy(alpha = 0.1f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 4.dp.toPx())
            )

            val sweep = (animatedKps / 20f).coerceIn(0f, 1f) * 180f
            drawArc(
                color = NeonGreen,
                startAngle = 180f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 4.dp.toPx())
            )

            val angle = 180f + sweep
            val needleLen = radius * 0.9f
            val endX = center.x + (needleLen * cos(Math.toRadians(angle.toDouble())).toFloat())
            val endY = center.y + (needleLen * sin(Math.toRadians(angle.toDouble())).toFloat())
            
            drawLine(
                color = NeonGreen,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx()
            )
            
            drawCircle(color = NeonGreen, radius = 4.dp.toPx(), center = center)
        }
        
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%.1f", animatedKps),
                color = NeonGreen,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "KPS",
                color = NeonGreen.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun IdActivityItem(id: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = id,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .width((count * 2).dp.coerceAtMost(80.dp))
                    .height(6.dp)
                    .background(NeonGreen.copy(alpha = 0.3f))
                    .border(0.5.dp, NeonGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = count.toString(),
                color = NeonGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(30.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

@Composable
fun RoomActivityGraph(kickHistory: List<Long>) {
    val now = System.currentTimeMillis()
    val bins = 40
    val binWidth = 1500L 
    val counts = IntArray(bins)
    
    kickHistory.forEach { ts ->
        val diff = now - ts
        val binIdx = (bins - 1) - (diff / binWidth).toInt()
        if (binIdx in 0 until bins) {
            counts[binIdx]++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .border(1.dp, NeonGreen, RoundedCornerShape(2.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            counts.forEach { count ->
                val heightFactor = (count / 10f).coerceIn(0.05f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(heightFactor)
                        .background(NeonGreen)
                )
            }
        }
    }
}
