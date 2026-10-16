package yt.ivy.librecanteen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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

// A calm, low-saturation palette: neutral greys with a single teal accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5F5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8E5),
    onPrimaryContainer = Color(0xFF0B2F2C),
    secondary = Color(0xFF4A6360),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECEA),
    onSecondaryContainer = Color(0xFF14201F),
    background = Color(0xFFF7F8F8),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF7F8F8),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFE9ECEC),
    onSurfaceVariant = Color(0xFF596060),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F2),
    surfaceContainerHigh = Color(0xFFEAEDED),
    surfaceContainerHighest = Color(0xFFE4E8E8),
    outline = Color(0xFF8B9393),
    outlineVariant = Color(0xFFD5DADA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FC4BD),
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF1D4A46),
    onPrimaryContainer = Color(0xFFB9E9E3),
    secondary = Color(0xFFB1CBC7),
    onSecondary = Color(0xFF1C3532),
    secondaryContainer = Color(0xFF2C3B39),
    onSecondaryContainer = Color(0xFFD2E8E4),
    background = Color(0xFF101313),
    onBackground = Color(0xFFE2E6E5),
    surface = Color(0xFF101313),
    onSurface = Color(0xFFE2E6E5),
    surfaceVariant = Color(0xFF252A2A),
    onSurfaceVariant = Color(0xFFA7AEAD),
    surfaceContainerLowest = Color(0xFF0B0E0E),
    surfaceContainerLow = Color(0xFF161A1A),
    surfaceContainer = Color(0xFF1B2020),
    surfaceContainerHigh = Color(0xFF222727),
    surfaceContainerHighest = Color(0xFF2A3030),
    outline = Color(0xFF6F7777),
    outlineVariant = Color(0xFF3A4140),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
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
fun LibreCanteenTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
