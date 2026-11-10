package yt.ivy.fastcanteen

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import yt.ivy.fastcanteen.data.AppSettings
import java.util.Locale

/**
 * Gives a context the language picked in settings. The strings follow it, and so does the default [Locale]
 * that dates and numbers use. Every context that shows text goes through here, because the app's own
 * configuration does not change when the setting does.
 */
object AppLocale {
    fun wrap(base: Context): Context {
        val locale = AppSettings(base).language.value.locale ?: Resources.getSystem().configuration.locales[0]
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
