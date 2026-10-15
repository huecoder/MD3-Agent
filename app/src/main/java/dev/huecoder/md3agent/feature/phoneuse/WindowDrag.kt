package dev.huecoder.md3agent.feature.phoneuse

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalViewConfiguration

// Window drags must be computed from raw screen coordinates: view-local
// positions shift under the finger when the window itself moves, which feeds
// the window's own motion back into the gesture and makes it oscillate.
private class DragSession {
    var down = Offset.Zero
    var last = Offset.Zero
    var dragging = false
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun Modifier.windowDrag(onDrag: (Offset) -> Unit, onEnd: () -> Unit, onTap: (() -> Unit)? = null): Modifier {
    val slop = LocalViewConfiguration.current.touchSlop
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onEnd)
    val latestTap by rememberUpdatedState(onTap)
    val session = remember { DragSession() }
    return pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                session.down = Offset(event.rawX, event.rawY)
                session.last = session.down
                session.dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val now = Offset(event.rawX, event.rawY)
                if (!session.dragging && (now - session.down).getDistance() > slop) session.dragging = true
                if (session.dragging) latestDrag(now - session.last)
                session.last = now
            }
            MotionEvent.ACTION_UP -> {
                if (session.dragging) latestEnd() else latestTap?.invoke()
                session.dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (session.dragging) latestEnd()
                session.dragging = false
            }
        }
        true
    }
}
