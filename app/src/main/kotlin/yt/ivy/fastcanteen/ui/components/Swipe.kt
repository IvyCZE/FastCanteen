package yt.ivy.fastcanteen.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * One step per horizontal drag: dragging left calls [onNext], dragging right calls [onPrevious], once the finger
 * has travelled far enough. Vertical scrolling and taps are left alone.
 */
fun Modifier.horizontalSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = composed {
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    pointerInput(threshold) {
        var travelled = 0f
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                when {
                    travelled <= -threshold -> { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); next() }
                    travelled >= threshold -> { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); previous() }
                }
                travelled = 0f
            },
            onDragCancel = { travelled = 0f },
            onHorizontalDrag = { _, delta -> travelled += delta },
        )
    }
}
