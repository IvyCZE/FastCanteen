package yt.ivy.librecanteen

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import yt.ivy.librecanteen.ui.AppRoot
import yt.ivy.librecanteen.ui.lock.AppLock
import yt.ivy.librecanteen.ui.theme.LibreCanteenTheme

// BiometricPrompt needs a FragmentActivity; it is still a ComponentActivity for Compose.
class MainActivity : FragmentActivity() {
    private val locked = MutableStateFlow(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LibreCanteenApp

        // A locked app should not show its content in screenshots or the recent apps list either.
        app.settings.biometricLock.onEach { enabled ->
            if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }.launchIn(lifecycleScope)

        setContent {
            LibreCanteenTheme {
                AppRoot(
                    repository = app.repository,
                    settings = app.settings,
                    lock = AppLock(this, locked) { locked.value = false },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - stoppedAt
        if (stoppedAt != 0L && away > RELOCK_AFTER_MS) locked.value = true
    }

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}
