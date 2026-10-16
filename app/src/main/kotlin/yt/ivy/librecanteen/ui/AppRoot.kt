package yt.ivy.librecanteen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.model.Money
import yt.ivy.librecanteen.data.AppSettings
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.reminders.Reminders
import yt.ivy.librecanteen.ui.history.HistoryScreen
import yt.ivy.librecanteen.ui.history.HistoryViewModel
import yt.ivy.librecanteen.ui.lock.AppLock
import yt.ivy.librecanteen.ui.lock.LockScreen
import yt.ivy.librecanteen.ui.login.LoginScreen
import yt.ivy.librecanteen.ui.login.LoginViewModel
import yt.ivy.librecanteen.ui.lunch.LunchScreen
import yt.ivy.librecanteen.ui.lunch.LunchViewModel
import yt.ivy.librecanteen.ui.settings.SettingsScreen
import yt.ivy.librecanteen.ui.settings.SettingsViewModel
import yt.ivy.librecanteen.ui.theme.RestaurantIcon
import yt.ivy.librecanteen.ui.theme.SwapHorizIcon

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
        key(account) { MainScaffold(repository, settings, lock) }
    }
}

private enum class Tab(val label: Int, val enabled: Boolean = true) {
    Info(R.string.tab_info),
    Lunch(R.string.tab_menu),

    /** Layout for the meal exchange is waiting until its behaviour is known. */
    Burza(R.string.tab_burza, enabled = false),
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Info -> Icons.Filled.Info
    Tab.Lunch -> RestaurantIcon
    Tab.Burza -> SwapHorizIcon
}

@Composable
private fun MainScaffold(repository: CanteenRepository, settings: AppSettings, lock: AppLock) {
    var selected by rememberSaveable { mutableIntStateOf(Tab.Lunch.ordinal) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val lunchVm: LunchViewModel = viewModel(factory = viewModelFactory { initializer { LunchViewModel(repository, settings) } })
    val historyVm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(repository, settings) } })

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsRoute(repository, settings, lock, settingsVm) { showSettings = false }
        return
    }

    val account by repository.account.collectAsStateWithLifecycle()
    val credit by repository.credit.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(account?.username.orEmpty(), credit) { showSettings = true } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab.ordinal,
                        enabled = tab.enabled,
                        onClick = { selected = tab.ordinal },
                        icon = { Icon(tab.icon(), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (Tab.entries[selected]) {
                Tab.Info -> {
                    val state by historyVm.state.collectAsStateWithLifecycle()
                    HistoryScreen(state, historyVm::load)
                }
                Tab.Lunch, Tab.Burza -> {
                    val state by lunchVm.state.collectAsStateWithLifecycle()
                    val mode by settings.calendarMode.collectAsStateWithLifecycle()
                    LunchScreen(
                        state = state,
                        mode = mode,
                        events = lunchVm.eventFlow,
                        snackbar = snackbar,
                        onRefresh = { lunchVm.load(forceAll = true) },
                        onPrevious = lunchVm::previous,
                        onNext = lunchVm::next,
                        onPreviousMonth = lunchVm::previousMonth,
                        onNextMonth = lunchVm::nextMonth,
                        onSelect = lunchVm::select,
                        onToggleMode = lunchVm::toggleMode,
                        onAction = lunchVm::apply,
                        onNote = lunchVm::saveNote,
                    )
                }
            }
        }
    }
}

/** Two rows: the app name with the settings button, and who is signed in with their credit. */
@Composable
private fun AppTopBar(username: String, credit: Money?, onSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
                }
            }
            Text(
                if (credit != null) "$username (${formatMoney(credit)})" else username,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsRoute(
    repository: CanteenRepository,
    settings: AppSettings,
    lock: AppLock,
    settingsVm: SettingsViewModel,
    onBack: () -> Unit,
) {
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
        settings = settings,
        onBack = onBack,
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
