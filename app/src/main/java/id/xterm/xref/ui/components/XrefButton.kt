package id.xterm.xref.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.ui.theme.DarkGreen900
import id.xterm.xref.ui.theme.NeonGreen

@Composable
fun XrefButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonGreen,
            contentColor = DarkGreen900,
            disabledContainerColor = NeonGreen.copy(alpha = 0.5f),
            disabledContentColor = DarkGreen900.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = text.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
