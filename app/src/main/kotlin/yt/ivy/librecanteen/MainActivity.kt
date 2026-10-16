package yt.ivy.librecanteen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import yt.ivy.librecanteen.ui.AppRoot
import yt.ivy.librecanteen.ui.theme.LibreCanteenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as LibreCanteenApp).repository
        setContent {
            LibreCanteenTheme {
                AppRoot(repository)
            }
        }
    }
}
