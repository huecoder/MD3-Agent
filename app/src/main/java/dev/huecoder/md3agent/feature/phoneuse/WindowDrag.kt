package dev.huecoder.md3agent.feature.phoneuse

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

@Composable
fun Modifier.windowDrag(onDrag: (Offset) -> Unit, onEnd: () -> Unit): Modifier {
    val view = LocalView.current
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onEnd)
    return pointerInput(view) {
        var last = Offset.Zero
        val origin = IntArray(2)
        fun onScreen(local: Offset): Offset {
            view.getLocationOnScreen(origin)
            return Offset(origin[0] + local.x, origin[1] + local.y)
        }
        detectDragGestures(
            onDragStart = { last = onScreen(it) },
            onDragEnd = { latestEnd() },
            onDragCancel = { latestEnd() },
            onDrag = { change, _ ->
                change.consume()
                val now = onScreen(change.position)
                latestDrag(now - last)
                last = now
            },
        )
    }
}
