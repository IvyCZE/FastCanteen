package yt.ivy.librecanteen.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier

/** What the visible screen reports to the top bar: a refresh is running, or the finger is pulling. */
@Stable
class TopBarRefreshState {
    var refreshing by mutableStateOf(false)
    var pull by mutableFloatStateOf(0f)
}

val LocalTopBarRefresh = compositionLocalOf { TopBarRefreshState() }

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
