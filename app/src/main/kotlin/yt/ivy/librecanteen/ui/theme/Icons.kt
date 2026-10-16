package yt.ivy.librecanteen.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Material Symbols paths for icons missing from material-icons-core, which keeps the APK small.
private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

val RestaurantIcon: ImageVector by lazy {
    icon(
        "Restaurant",
        "M11,9H9V2H7v7H5V2H3v7c0,2.12 1.66,3.84 3.75,3.97V22h2.5v-9.03C11.34,12.84 13,11.12 13,9V2h-2V9z" +
            "M16,6v8h2.5v8H21V2C18.24,2 16,4.24 16,6z",
    )
}

val SwapHorizIcon: ImageVector by lazy {
    icon("SwapHoriz", "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3z" + "M21,9l-3.99,-4v3H10v2h7.01v3L21,9z")
}
