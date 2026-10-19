package yt.ivy.librecanteen

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import yt.ivy.librecanteen.data.AppSettings
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.data.CredentialVault
import yt.ivy.librecanteen.data.FileMenuCache
import java.io.File

class LibreCanteenApp : Application() {
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
