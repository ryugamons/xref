package id.xterm.xref.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xterm.xref.ui.theme.DarkGreen900
import id.xterm.xref.ui.theme.NeonGreen

@Composable
fun XrefButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = NeonGreen,
    contentColor: Color = DarkGreen900,
    borderColor: Color = Color.Transparent,
    height: Dp = 48.dp,
    fontSize: TextUnit = 14.sp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(8.dp))
                } else Modifier
            ),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = contentPadding
    ) {
        Text(
            text = text.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = fontSize
        )
    }
}
