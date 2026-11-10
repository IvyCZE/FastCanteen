package yt.ivy.fastcanteen

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import yt.ivy.fastcanteen.data.AppSettings
import yt.ivy.fastcanteen.data.CanteenRepository
import yt.ivy.fastcanteen.data.CredentialVault
import yt.ivy.fastcanteen.data.FileMenuCache
import java.io.File

class FastCanteenApp : Application() {
    val settings: AppSettings by lazy { AppSettings(this) }

    /** Kept here rather than in the activity, so changing the language (which recreates it) does not relock the app. */
    val locked = MutableStateFlow(true)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    val repository: CanteenRepository by lazy {
        // noBackupFilesDir keeps cached orders out of device backups.
        CanteenRepository(CredentialVault(this), FileMenuCache(File(noBackupFilesDir, "menus.json")))
    }
}
