package dev.huecoder.md3agent.feature.phoneuse

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

// Deltas for moving a window must come from raw screen coordinates: view-local
// coordinates shift under the finger when the window itself moves, which feeds
// the window's own motion back into the gesture and makes it oscillate.
private class RawPointer {
    var position = Offset.Zero

    fun track(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    position = Offset(event.rawX, event.rawY)
            }
            false
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun Modifier.windowDrag(onDrag: (Offset) -> Unit, onEnd: () -> Unit): Modifier {
    val view = LocalView.current
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onEnd)
    val raw = remember(view) { RawPointer().also { it.track(view) } }
    return pointerInput(raw) {
        var last = Offset.Zero
        detectDragGestures(
            onDragStart = { last = raw.position },
            onDragEnd = { latestEnd() },
            onDragCancel = { latestEnd() },
            onDrag = { change, _ ->
                change.consume()
                val now = raw.position
                latestDrag(now - last)
                last = now
            },
        )
    }
}
