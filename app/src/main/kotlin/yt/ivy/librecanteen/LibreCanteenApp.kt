package yt.ivy.librecanteen

import android.app.Application
import yt.ivy.librecanteen.data.AppSettings
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.data.CredentialVault
import yt.ivy.librecanteen.data.FileMenuCache
import java.io.File

class LibreCanteenApp : Application() {
    val settings: AppSettings by lazy { AppSettings(this) }

    val repository: CanteenRepository by lazy {
        // noBackupFilesDir keeps cached orders out of device backups.
        CanteenRepository(CredentialVault(this), FileMenuCache(File(noBackupFilesDir, "menus.json")))
    }
}
