package yt.ivy.librecanteen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** What the visible screen reports to the top bar: a refresh is running, or the finger is pulling. */
@Stable
class TopBarRefreshState {
    var refreshing by mutableStateOf(false)
    var pull by mutableFloatStateOf(0f)
}

val LocalTopBarRefresh = compositionLocalOf { TopBarRefreshState() }

private const val SPINNER_SWEEP = 270f

/**
 * A fixed-length arc. While pulling it grows with the finger and turns with it; once loading starts it keeps its
 * length and turns at a constant speed from where the finger left it, so there is no jump between the two.
 */
@Composable
fun RefreshSpinner(pull: Float, refreshing: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val fraction by rememberUpdatedState(pull.coerceIn(0f, 1f))
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            rotation.snapTo(fraction * SPINNER_SWEEP)
            while (true) rotation.animateTo(rotation.value + 360f, tween(SPIN_MILLIS, easing = LinearEasing))
        }
    }
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val sweep = if (refreshing) SPINNER_SWEEP else SPINNER_SWEEP * fraction
        val angle = if (refreshing) rotation.value else fraction * SPINNER_SWEEP
        drawArc(
            color = color,
            startAngle = angle - 90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

private const val SPIN_MILLIS = 900

/**
 * Pull-to-refresh whose indicator is drawn in the top bar, not over the content. The gesture is the
 * stock one; only the state is handed to [LocalTopBarRefresh].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val top = LocalTopBarRefresh.current
    val pullState = rememberPullToRefreshState()
    SideEffect { top.refreshing = isRefreshing }
    LaunchedEffect(pullState) {
        snapshotFlow { pullState.distanceFraction }.collect { top.pull = it }
    }
    DisposableEffect(top) {
        onDispose {
            top.refreshing = false
            top.pull = 0f
        }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullState,
        indicator = {},
        content = content,
    )
}
