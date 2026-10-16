package yt.ivy.librecanteen.ui.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.StateFlow
import yt.ivy.librecanteen.R

/** Gate in front of the app, using the device's biometrics (or screen lock where the OS allows it). */
class AppLock(
    private val activity: FragmentActivity,
    val locked: StateFlow<Boolean>,
    private val onUnlocked: () -> Unit,
) {
    // Combining biometrics with the screen lock is only supported from Android 11.
    private val authenticators =
        if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    val available: Boolean
        get() = BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private var prompting = false

    fun authenticate(onResult: (Boolean) -> Unit = {}) {
        if (prompting) return
        prompting = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    onUnlocked()
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setAllowedAuthenticators(authenticators)
            .apply {
                if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText(activity.getString(R.string.dialog_close))
            }
            .build()
        prompt.authenticate(info)
    }
}
