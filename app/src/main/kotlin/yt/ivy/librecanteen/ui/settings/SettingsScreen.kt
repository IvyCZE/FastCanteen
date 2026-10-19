package yt.ivy.librecanteen.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.core.model.AllergenSettings
import yt.ivy.librecanteen.data.Account
import yt.ivy.librecanteen.data.AppSettings
import yt.ivy.librecanteen.data.CalendarMode
import yt.ivy.librecanteen.data.ThemeMode
import yt.ivy.librecanteen.ui.theme.PaletteIcon
import yt.ivy.librecanteen.ui.theme.dynamicColorSupported
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.asString
import yt.ivy.librecanteen.ui.toUiText

data class PasswordState(
    val working: Boolean = false,
    val error: UiText? = null,
    val changed: Boolean = false,
)

data class AllergenUiState(
    val loading: Boolean = false,
    val settings: AllergenSettings? = null,
    val saving: Boolean = false,
    val error: UiText? = null,
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val repository: CanteenRepository,
    private val appSettings: AppSettings,
) : ViewModel() {
    private val _allergens = MutableStateFlow(AllergenUiState())
    val allergens: StateFlow<AllergenUiState> = _allergens.asStateFlow()

    fun loadAllergens() {
        _allergens.value = AllergenUiState(loading = true)
        viewModelScope.launch {
            try {
                val settings = repository.allergenSettings()
                appSettings.setAllergenCodes(settings.selectedCodes)
                _allergens.value = AllergenUiState(settings = settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _allergens.value = AllergenUiState(error = e.toUiText())
            }
        }
    }

    fun saveAllergens(codes: Set<String>) {
        val current = _allergens.value.settings ?: return
        if (_allergens.value.saving) return
        _allergens.value = _allergens.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val saved = repository.saveAllergenSettings(codes, current.displayMode)
                appSettings.setAllergenCodes(saved.selectedCodes)
                _allergens.value = AllergenUiState(settings = saved, saved = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _allergens.value = _allergens.value.copy(saving = false, error = e.toUiText())
            }
        }
    }

    fun closeAllergens() {
        _allergens.value = AllergenUiState()
    }

    private val _password = MutableStateFlow(PasswordState())
    val password: StateFlow<PasswordState> = _password.asStateFlow()

    fun changePassword(current: String, new: String) {
        if (_password.value.working) return
        _password.value = PasswordState(working = true)
        viewModelScope.launch {
            try {
                repository.changePassword(current, new)
                _password.value = PasswordState(changed = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _password.value = PasswordState(error = e.toUiText())
            }
        }
    }

    fun resetPassword() {
        _password.value = PasswordState()
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}

private enum class SettingsPage(val title: Int, val summary: Int, val icon: ImageVector) {
    Customization(R.string.settings_cat_customize, R.string.settings_cat_customize_summary, PaletteIcon),
    Account(R.string.settings_cat_account, R.string.settings_cat_account_summary, Icons.Filled.Person),
    Allergens(R.string.settings_allergens, R.string.settings_cat_allergens_summary, Icons.Filled.Warning),
    Notifications(R.string.settings_cat_notifications, R.string.settings_cat_notifications_summary, Icons.Filled.Notifications),
    About(R.string.settings_about, R.string.settings_version, Icons.Filled.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    account: Account?,
    password: PasswordState,
    onChangePassword: (current: String, new: String) -> Unit,
    onPasswordDialogClosed: () -> Unit,
    onSignOut: () -> Unit,
    biometricAvailable: Boolean,
    biometricLock: Boolean,
    onBiometricLock: (Boolean) -> Unit,
    reminders: Boolean,
    onReminders: (Boolean) -> Unit,
    allergens: AllergenUiState,
    onOpenAllergens: () -> Unit,
    onSaveAllergens: (Set<String>) -> Unit,
    onCloseAllergens: () -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let(SettingsPage::valueOf)
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    // Registered after the one in AppRoot, so it wins while a page is open and leaves the settings list on the next back.
    BackHandler(enabled = page != null) { pageName = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page?.title ?: R.string.tab_settings)) },
                navigationIcon = {
                    IconButton(onClick = { if (page != null) pageName = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            when (page) {
                null -> SettingsHome(version) { pageName = it.name }
                SettingsPage.Customization -> PageColumn { CustomizationPage(settings) }
                SettingsPage.Account -> PageColumn {
                    AccountPage(
                        account, password, onChangePassword, onPasswordDialogClosed, onSignOut,
                        biometricAvailable, biometricLock, onBiometricLock,
                    )
                }
                SettingsPage.Allergens -> PageColumn {
                    AllergensPage(allergens, onOpen = onOpenAllergens, onSave = onSaveAllergens, onClose = onCloseAllergens)
                }
                SettingsPage.Notifications -> PageColumn {
                    SettingsCard {
                        SwitchRow(stringResource(R.string.settings_reminders), stringResource(R.string.settings_reminders_hint), reminders, true, onReminders)
                    }
                }
                SettingsPage.About -> PageColumn {
                    SettingsCard {
                        Text(
                            stringResource(R.string.settings_about_text, version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** The rounded backdrop every item in the app sits on. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingsHome(version: String, onOpen: (SettingsPage) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsPage.entries.forEach { entry ->
            Surface(
                onClick = { onOpen(entry) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(entry.title)) },
                    supportingContent = {
                        Text(if (entry == SettingsPage.About) stringResource(entry.summary, version) else stringResource(entry.summary))
                    },
                    leadingContent = { Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun CustomizationPage(settings: AppSettings) {
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
    val calendarMode by settings.calendarMode.collectAsStateWithLifecycle()

    SettingsCard {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Column {
            ThemeMode.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = themeMode == option, role = Role.RadioButton) { settings.setThemeMode(option) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = themeMode == option, onClick = null)
                    Text(
                        stringResource(
                            when (option) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                                ThemeMode.AMOLED -> R.string.theme_amoled
                            },
                        ),
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
    SettingsCard {
        SwitchRow(
            stringResource(R.string.settings_dynamic_color),
            stringResource(if (dynamicColorSupported) R.string.settings_dynamic_color_hint else R.string.settings_dynamic_color_unavailable),
            dynamicColor && dynamicColorSupported,
            dynamicColorSupported,
            settings::setDynamicColor,
        )
    }
    SettingsCard {
        Text(stringResource(R.string.settings_calendar_mode), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CalendarMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = calendarMode == option,
                    onClick = { settings.setCalendarMode(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, CalendarMode.entries.size),
                ) {
                    Text(stringResource(if (option == CalendarMode.WEEK) R.string.calendar_week else R.string.calendar_month))
                }
            }
        }
        Text(
            stringResource(R.string.settings_calendar_mode_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountPage(
    account: Account?,
    password: PasswordState,
    onChangePassword: (current: String, new: String) -> Unit,
    onPasswordDialogClosed: () -> Unit,
    onSignOut: () -> Unit,
    biometricAvailable: Boolean,
    biometricLock: Boolean,
    onBiometricLock: (Boolean) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    SettingsCard {
        account?.let {
            InfoRow(stringResource(R.string.settings_username), it.username)
            InfoRow(stringResource(R.string.settings_canteen), it.address)
        }
        OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_change_password))
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_sign_out))
        }
    }
    SettingsCard {
        SwitchRow(
            stringResource(R.string.settings_lock),
            stringResource(if (biometricAvailable) R.string.settings_lock_hint else R.string.settings_lock_unavailable),
            biometricLock,
            biometricAvailable || biometricLock,
            onBiometricLock,
        )
    }
    if (showDialog) {
        ChangePasswordDialog(
            state = password,
            onConfirm = onChangePassword,
            onDismiss = {
                showDialog = false
                onPasswordDialogClosed()
            },
        )
    }
}

/** The allergen list is read from the canteen when the page opens and written back with the save button. */
@Composable
private fun AllergensPage(state: AllergenUiState, onOpen: () -> Unit, onSave: (Set<String>) -> Unit, onClose: () -> Unit) {
    DisposableEffect(Unit) {
        onOpen()
        onDispose(onClose)
    }
    val settings = state.settings
    var selected by remember(settings) { mutableStateOf(settings?.selectedCodes.orEmpty()) }

    if (settings == null) {
        if (state.error != null) {
            Text(state.error.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpen) { Text(stringResource(R.string.retry)) }
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }

    SettingsCard {
        Text(
            stringResource(R.string.allergens_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            settings.allergens.forEach { allergen ->
                val isGroup = allergen.code.last().isDigit()
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(allergen.code in selected, enabled = !state.saving, role = Role.Checkbox) { on ->
                            selected = if (on) selected + allergen.code else selected - allergen.code
                        }
                        .padding(start = if (isGroup) 0.dp else 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = allergen.code in selected, onCheckedChange = null)
                    Text(
                        "${allergen.code}  ${allergen.name.substringBefore(" - ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
    if (state.error != null) {
        Text(state.error.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    val changed = selected != settings.selectedCodes
    if (state.saved && !changed) {
        Text(stringResource(R.string.allergens_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = { onSave(selected) }, enabled = changed && !state.saving, modifier = Modifier.fillMaxWidth()) {
        if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.note_save))
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChangePasswordDialog(
    state: PasswordState,
    onConfirm: (current: String, new: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }

    val mismatch = repeat.isNotEmpty() && new != repeat
    val same = new.isNotEmpty() && new == current
    val valid = current.isNotEmpty() && new.isNotEmpty() && new == repeat && !same

    if (state.changed) {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.password_changed)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!state.working) onDismiss() },
        title = { Text(stringResource(R.string.settings_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, stringResource(R.string.password_current))
                PasswordField(new, { new = it }, stringResource(R.string.password_new))
                PasswordField(repeat, { repeat = it }, stringResource(R.string.password_repeat))
                val message = when {
                    mismatch -> stringResource(R.string.password_mismatch)
                    same -> stringResource(R.string.password_same)
                    else -> state.error?.asString()
                }
                if (message != null) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current, new) }, enabled = valid && !state.working) {
                if (state.working) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.password_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.working) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
