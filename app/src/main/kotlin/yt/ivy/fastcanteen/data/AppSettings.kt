package yt.ivy.fastcanteen.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

enum class CalendarMode { WEEK, MONTH }

/** [locale] is null for "follow the system", which is resolved when the app applies it. */
enum class AppLanguage(val locale: Locale?) { SYSTEM(null), CZECH(Locale.forLanguageTag("cs")), ENGLISH(Locale.ENGLISH) }

/** Non-secret preferences. Credentials live in [CredentialVault], never here. */
class AppSettings(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("settings", Context.MODE_PRIVATE))

    private val _biometricLock = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false))
    val biometricLock: StateFlow<Boolean> = _biometricLock.asStateFlow()

    private val _reminders = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS, false))
    val reminders: StateFlow<Boolean> = _reminders.asStateFlow()

    private val _themeMode = MutableStateFlow(readEnum(KEY_THEME, ThemeMode.SYSTEM))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _calendarMode = MutableStateFlow(readEnum(KEY_CALENDAR, CalendarMode.WEEK))
    val calendarMode: StateFlow<CalendarMode> = _calendarMode.asStateFlow()

    private val _language = MutableStateFlow(readEnum(KEY_LANGUAGE, AppLanguage.SYSTEM))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _language.value = language
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setCalendarMode(mode: CalendarMode) {
        prefs.edit().putString(KEY_CALENDAR, mode.name).apply()
        _calendarMode.value = mode
    }

    private inline fun <reified E : Enum<E>> readEnum(key: String, default: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    /** Full allergen names by code, kept so the details sheet works offline. */
    var allergenNames: Map<String, String>
        get() = prefs.getStringSet(KEY_ALLERGEN_NAMES, emptySet()).orEmpty()
            .mapNotNull { it.split(SEPARATOR, limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        set(value) = prefs.edit().putStringSet(KEY_ALLERGEN_NAMES, value.map { "${it.key}$SEPARATOR${it.value}" }.toSet()).apply()

    fun setBiometricLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK, enabled).apply()
        _biometricLock.value = enabled
    }

    fun setReminders(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS, enabled).apply()
        _reminders.value = enabled
    }

    /** ISO date of the Monday of the week the user was last reminded about. */
    var lastReminderWeek: String?
        get() = prefs.getString(KEY_LAST_REMINDER, null)
        set(value) = prefs.edit().putString(KEY_LAST_REMINDER, value).apply()

    private val _allergenCodes = MutableStateFlow(prefs.getStringSet(KEY_ALLERGENS, emptySet()).orEmpty().toSet())

    /** Allergens the user selected in the canteen settings, kept for offline use. */
    val allergenCodes: StateFlow<Set<String>> = _allergenCodes.asStateFlow()

    fun setAllergenCodes(codes: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLERGENS, codes).apply()
        _allergenCodes.value = codes
    }

    fun clearAccountData() {
        prefs.edit().remove(KEY_LAST_REMINDER).remove(KEY_ALLERGENS).apply()
        _allergenCodes.value = emptySet()
    }

    private companion object {
        const val SEPARATOR = '\u001f'
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_CALENDAR = "calendar_mode"
        const val KEY_LANGUAGE = "language"
        const val KEY_ALLERGEN_NAMES = "allergen_names"
        const val KEY_LOCK = "biometric_lock"
        const val KEY_REMINDERS = "reminders"
        const val KEY_LAST_REMINDER = "last_reminder_week"
        const val KEY_ALLERGENS = "allergens"
    }
}
