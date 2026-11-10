package yt.ivy.fastcanteen.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screen width from which the bottom bar becomes a navigation rail (the Material "medium" window class). */
val RailMinWidth = 600.dp

/** Longest line length that still reads well; lists and forms stop growing here. */
val ContentMaxWidth = 640.dp

/** Keeps [content] centered and at most [maxWidth] wide, so lists do not stretch across a tablet. */
@Composable
fun CenteredContent(modifier: Modifier = Modifier, maxWidth: Dp = ContentMaxWidth, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
