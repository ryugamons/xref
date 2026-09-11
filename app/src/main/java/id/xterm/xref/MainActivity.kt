package id.xterm.xref

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.xterm.xref.ui.MainScreen
import id.xterm.xref.ui.theme.GreenlightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GreenlightTheme {
                MainScreen()
            }
        }
    }
}
