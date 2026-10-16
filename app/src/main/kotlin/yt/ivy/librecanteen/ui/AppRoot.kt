package yt.ivy.librecanteen.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.data.AppSettings
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.reminders.Reminders
import yt.ivy.librecanteen.ui.lock.AppLock
import yt.ivy.librecanteen.ui.lock.LockScreen
import yt.ivy.librecanteen.ui.history.HistoryScreen
import yt.ivy.librecanteen.ui.history.HistoryViewModel
import yt.ivy.librecanteen.ui.login.LoginScreen
import yt.ivy.librecanteen.ui.login.LoginViewModel
import yt.ivy.librecanteen.ui.settings.SettingsScreen
import yt.ivy.librecanteen.ui.settings.SettingsViewModel
import yt.ivy.librecanteen.ui.week.WeekScreen
import yt.ivy.librecanteen.ui.week.WeekViewModel

@Composable
fun AppRoot(repository: CanteenRepository, settings: AppSettings, lock: AppLock) {
    val lockEnabled by settings.biometricLock.collectAsStateWithLifecycle()
    val locked by lock.locked.collectAsStateWithLifecycle()
    if (lockEnabled && locked) {
        LockScreen(onUnlock = { lock.authenticate() })
        return
    }

    val account by repository.account.collectAsStateWithLifecycle()

    if (account == null) {
        val vm: LoginViewModel = viewModel(factory = viewModelFactory { initializer { LoginViewModel(repository) } })
        val state by vm.state.collectAsStateWithLifecycle()
        LoginScreen(state, vm::signIn)
    } else {
        // Keyed by account so a different login never sees the previous user's screens.
        androidx.compose.runtime.key(account) { MainScaffold(repository, settings, lock) }
    }
}

private enum class Tab(val label: Int) {
    Menu(R.string.tab_menu),
    History(R.string.tab_history),
    Settings(R.string.tab_settings),
}

@Composable
private fun MainScaffold(repository: CanteenRepository, settings: AppSettings, lock: AppLock) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    val weekVm: WeekViewModel = viewModel(factory = viewModelFactory { initializer { WeekViewModel(repository, settings) } })
    val historyVm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(repository, settings) } })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.Menu -> Icons.Filled.Home
                                    Tab.History -> Icons.AutoMirrored.Filled.List
                                    Tab.Settings -> Icons.Filled.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        androidx.compose.foundation.layout.Box(modifier) {
            when (Tab.entries[selected]) {
                Tab.Menu -> {
                    val state by weekVm.state.collectAsStateWithLifecycle()
                    WeekScreen(
                        state = state,
                        events = weekVm.eventFlow,
                        snackbar = snackbar,
                        onRefresh = weekVm::load,
                        onPrevious = weekVm::previousWeek,
                        onNext = weekVm::nextWeek,
                        onAction = weekVm::apply,
                        onNote = weekVm::saveNote,
                    )
                }
                Tab.History -> {
                    val state by historyVm.state.collectAsStateWithLifecycle()
                    HistoryScreen(state, historyVm::load)
                }
                Tab.Settings -> {
                    val account by repository.account.collectAsStateWithLifecycle()
                    val password by settingsVm.password.collectAsStateWithLifecycle()
                    val allergens by settingsVm.allergens.collectAsStateWithLifecycle()
                    val biometricLock by settings.biometricLock.collectAsStateWithLifecycle()
                    val reminders by settings.reminders.collectAsStateWithLifecycle()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val applyReminders = { enabled: Boolean ->
                        settings.setReminders(enabled)
                        Reminders.schedule(context, enabled)
                    }
                    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    ) { granted -> applyReminders(granted) }
                    SettingsScreen(
                        account = account,
                        password = password,
                        onChangePassword = settingsVm::changePassword,
                        onPasswordDialogClosed = settingsVm::resetPassword,
                        onSignOut = {
                            Reminders.schedule(context, false)
                            settings.setReminders(false)
                            settings.clearAccountData()
                            settingsVm.signOut()
                        },
                        biometricAvailable = lock.available,
                        biometricLock = biometricLock,
                        onBiometricLock = { enable ->
                            // Ask once now, so a user who cannot authenticate is never locked out.
                            if (enable) lock.authenticate { ok -> if (ok) settings.setBiometricLock(true) }
                            else settings.setBiometricLock(false)
                        },
                        allergens = allergens,
                        onOpenAllergens = settingsVm::loadAllergens,
                        onSaveAllergens = settingsVm::saveAllergens,
                        onCloseAllergens = settingsVm::closeAllergens,
                        reminders = reminders,
                        onReminders = { enable ->
                            if (enable && android.os.Build.VERSION.SDK_INT >= 33) {
                                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                applyReminders(enable)
                            }
                        },
                    )
                }
            }
        }
    }
}
