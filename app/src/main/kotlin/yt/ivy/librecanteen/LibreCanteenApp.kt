package yt.ivy.librecanteen

import android.app.Application
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.data.CredentialVault

class LibreCanteenApp : Application() {
    val repository: CanteenRepository by lazy { CanteenRepository(CredentialVault(this)) }
}
