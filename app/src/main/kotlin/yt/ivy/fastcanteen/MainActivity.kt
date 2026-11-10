package yt.ivy.fastcanteen

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import yt.ivy.fastcanteen.ui.theme.isDark
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import yt.ivy.fastcanteen.ui.AppRoot
import yt.ivy.fastcanteen.ui.lock.AppLock
import yt.ivy.fastcanteen.ui.theme.FastCanteenTheme

// BiometricPrompt needs a FragmentActivity; it is still a ComponentActivity for Compose.
class MainActivity : FragmentActivity() {
    private val locked by lazy { (application as FastCanteenApp).locked }
    private var stoppedAt = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FastCanteenApp

        // A locked app should not show its content in screenshots or the recent apps list either.
        app.settings.biometricLock.onEach { enabled ->
            if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }.launchIn(lifecycleScope)

        // Strings and the default locale are read when the activity is created, so a new language needs a new one.
        app.settings.language.drop(1).onEach { recreate() }.launchIn(lifecycleScope)

        setContent {
            val themeMode by app.settings.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by app.settings.dynamicColor.collectAsStateWithLifecycle()
            val dark = themeMode.isDark()
            // The system bar icons must follow the app's theme, not the system's.
            SideEffect {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            FastCanteenTheme(themeMode, dynamicColor) {
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
