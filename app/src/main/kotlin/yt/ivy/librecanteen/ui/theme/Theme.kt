package yt.ivy.librecanteen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import yt.ivy.librecanteen.data.ThemeMode
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Teal-blue accent (seed #1D8085) on slightly tinted neutrals.
private val LightColors = lightColorScheme(
    primary = Color(0xFF17747A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDEBEF),
    onPrimaryContainer = Color(0xFF00363A),
    secondary = Color(0xFF4B6769),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8EB),
    onSecondaryContainer = Color(0xFF051F22),
    tertiary = Color(0xFF4E5E7E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E2FF),
    onTertiaryContainer = Color(0xFF091B37),
    background = Color(0xFFF4FAFA),
    onBackground = Color(0xFF161D1E),
    surface = Color(0xFFF4FAFA),
    onSurface = Color(0xFF161D1E),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEEF5F6),
    surfaceContainer = Color(0xFFE8F0F1),
    surfaceContainerHigh = Color(0xFFE2EAEB),
    surfaceContainerHighest = Color(0xFFDCE4E5),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBEC8C9),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D3DA),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = Color(0xFF9EEFF6),
    secondary = Color(0xFFB1CBCE),
    onSecondary = Color(0xFF1C3437),
    secondaryContainer = Color(0xFF334B4E),
    onSecondaryContainer = Color(0xFFCDE8EB),
    tertiary = Color(0xFFB6C6EA),
    onTertiary = Color(0xFF20304D),
    tertiaryContainer = Color(0xFF364665),
    onTertiaryContainer = Color(0xFFD6E2FF),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDEE3E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE3E4),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF161D1E),
    surfaceContainer = Color(0xFF1A2122),
    surfaceContainerHigh = Color(0xFF252B2D),
    surfaceContainerHighest = Color(0xFF303638),
    outline = Color(0xFF899394),
    outlineVariant = Color(0xFF3F4849),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** True black backgrounds for OLED screens; cards stay just above black so they remain distinguishable. */
private fun ColorScheme.amoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0F0F0F),
    surfaceContainerHigh = Color(0xFF171717),
    surfaceContainerHighest = Color(0xFF1F1F1F),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

private val Base = Typography()

private val AppTypography = Typography(
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Medium),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 21.sp),
)

/** Numbers that line up in columns: prices, credit and dates. */
val NumericStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

/** Dynamic color follows the wallpaper and needs Android 12; older versions use the built-in teal palette. */
val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun LibreCanteenTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark()
    val context = LocalContext.current
    val base = when {
        dynamicColor && dynamicColorSupported ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = if (mode == ThemeMode.AMOLED) base.amoled() else base,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
