package yt.ivy.fastcanteen.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import yt.ivy.fastcanteen.R
import yt.ivy.fastcanteen.core.model.AllergenSettings
import yt.ivy.fastcanteen.data.Account
import yt.ivy.fastcanteen.ui.components.ContentMaxWidth
import yt.ivy.fastcanteen.data.AppLanguage
import yt.ivy.fastcanteen.data.AppSettings
import yt.ivy.fastcanteen.data.CalendarMode
import yt.ivy.fastcanteen.data.ThemeMode
import yt.ivy.fastcanteen.ui.theme.LanguageIcon
import yt.ivy.fastcanteen.ui.theme.PaletteIcon
import yt.ivy.fastcanteen.ui.theme.dynamicColorSupported
import yt.ivy.fastcanteen.data.CanteenRepository
import yt.ivy.fastcanteen.ui.UiText
import yt.ivy.fastcanteen.ui.asString
import yt.ivy.fastcanteen.ui.toUiText

data class PasswordState(
    val working: Boolean = false,
    val error: UiText? = null,
    val changed: Boolean = false,
)

data class AllergenUiState(
    val loading: Boolean = false,
    /** What the canteen has stored. */
    val settings: AllergenSettings? = null,
    /** What the user has ticked; may be ahead of [settings] while a save is waiting or running. */
    val selected: Set<String> = emptySet(),
    val saving: Boolean = false,
    val error: UiText? = null,
    /** The last save went through and nothing was changed since. */
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val repository: CanteenRepository,
    private val appSettings: AppSettings,
) : ViewModel() {
    private val _allergens = MutableStateFlow(AllergenUiState())
    val allergens: StateFlow<AllergenUiState> = _allergens.asStateFlow()

    // Ticking several boxes in a row costs the school's server one request, not one per box.
    private val allergenSaver = DebouncedSaver<Set<String>>(viewModelScope, ALLERGEN_AUTOSAVE_MS) { persistAllergens(it) }

    fun loadAllergens() {
        _allergens.value = AllergenUiState(loading = true)
        viewModelScope.launch {
            // A save left over from the last visit must land before the settings are read back.
            allergenSaver.flush()
            try {
                val settings = repository.allergenSettings()
                appSettings.setAllergenCodes(settings.selectedCodes)
                _allergens.value = AllergenUiState(settings = settings, selected = settings.selectedCodes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _allergens.value = AllergenUiState(error = e.toUiText())
            }
        }
    }

    fun selectAllergens(codes: Set<String>) {
        if (_allergens.value.settings == null) return
        _allergens.update { it.copy(selected = codes, saved = false, error = null) }
        allergenSaver.submit(codes)
    }

    /** Called when the page is left, so a change made in the last moments is not lost. */
    fun closeAllergens() {
        viewModelScope.launch { allergenSaver.flush() }
    }

    private suspend fun persistAllergens(codes: Set<String>) {
        val current = _allergens.value.settings ?: return
        if (codes == current.selectedCodes) {
            _allergens.update { it.copy(saved = false) }
            return
        }
        _allergens.update { it.copy(saving = true) }
        try {
            val saved = repository.saveAllergenSettings(codes, current.displayMode)
            appSettings.setAllergenCodes(saved.selectedCodes)
            _allergens.update { it.copy(settings = saved, saving = false, saved = it.selected == saved.selectedCodes) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _allergens.update { it.copy(saving = false, error = e.toUiText()) }
        }
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

    private companion object {
        const val ALLERGEN_AUTOSAVE_MS = 1500L
    }
}

private enum class SettingsPage(val title: Int, val summary: Int, val icon: ImageVector) {
    Customization(R.string.settings_cat_customize, R.string.settings_cat_customize_summary, PaletteIcon),
    Language(R.string.settings_cat_language, R.string.settings_cat_language_summary, LanguageIcon),
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
    onSelectAllergens: (Set<String>) -> Unit,
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
        Box(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                when (page) {
                    null -> SettingsHome(version) { pageName = it.name }
                    SettingsPage.Customization -> PageColumn { CustomizationPage(settings) }
                    SettingsPage.Language -> PageColumn { LanguagePage(settings) }
                    SettingsPage.Account -> PageColumn {
                        AccountPage(
                            account, password, onChangePassword, onPasswordDialogClosed, onSignOut,
                            biometricAvailable, biometricLock, onBiometricLock,
                        )
                    }
                    SettingsPage.Allergens -> PageColumn {
                        AllergensPage(allergens, onOpen = onOpenAllergens, onSelect = onSelectAllergens, onClose = onCloseAllergens)
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
private fun <T> RadioChoices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column {
        options.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = selected == option, role = Role.RadioButton) { onSelect(option) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == option, onClick = null)
                Text(label(option), modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun LanguagePage(settings: AppSettings) {
    val language by settings.language.collectAsStateWithLifecycle()
    SettingsCard {
        RadioChoices(
            options = AppLanguage.entries,
            selected = language,
            label = {
                stringResource(
                    when (it) {
                        AppLanguage.SYSTEM -> R.string.language_system
                        AppLanguage.CZECH -> R.string.language_czech
                        AppLanguage.ENGLISH -> R.string.language_english
                    },
                )
            },
            onSelect = settings::setLanguage,
        )
    }
}

@Composable
private fun CustomizationPage(settings: AppSettings) {
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
    val calendarMode by settings.calendarMode.collectAsStateWithLifecycle()

    SettingsCard {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        RadioChoices(
            options = ThemeMode.entries,
            selected = themeMode,
            label = {
                stringResource(
                    when (it) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                        ThemeMode.AMOLED -> R.string.theme_amoled
                    },
                )
            },
            onSelect = settings::setThemeMode,
        )
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

/** Read from the canteen when the page opens; every change is saved automatically a moment after the last tick. */
@Composable
private fun AllergensPage(state: AllergenUiState, onOpen: () -> Unit, onSelect: (Set<String>) -> Unit, onClose: () -> Unit) {
    DisposableEffect(Unit) {
        onOpen()
        onDispose(onClose)
    }
    val settings = state.settings

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
                        .toggleable(allergen.code in state.selected, role = Role.Checkbox) { on ->
                            onSelect(if (on) state.selected + allergen.code else state.selected - allergen.code)
                        }
                        .padding(start = if (isGroup) 0.dp else 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = allergen.code in state.selected, onCheckedChange = null)
                    Text(
                        "${allergen.code}  ${allergen.name.substringBefore(" - ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
    val unsaved = state.selected != settings.selectedCodes
    when {
        state.error != null -> Text(state.error.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        state.saving || unsaved -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.allergens_saving), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.saved -> Text(stringResource(R.string.allergens_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
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
