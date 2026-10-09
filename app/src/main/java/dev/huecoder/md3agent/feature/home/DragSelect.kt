package dev.huecoder.md3agent.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

fun Modifier.dragToSelect(
    list: LazyListState,
    scope: CoroutineScope,
    isSelectable: (Any) -> Boolean,
    onRange: (anchor: String, current: String) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(list) {
    val edge = AUTO_SCROLL_EDGE.toPx()
    val maxSpeed = AUTO_SCROLL_SPEED.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val anchor = list.keyAt(down.position.y)?.takeIf(isSelectable) as? String ?: return@awaitEachGesture
        val letGo = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitReleaseOrSlide(down) }
        if (letGo != null) return@awaitEachGesture

        var finger = down.position
        var autoScroll: Job? = null
        fun extendTo(y: Float) {
            val key = list.keyAt(y)?.takeIf(isSelectable) as? String ?: return
            onRange(anchor, key)
        }
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (change.positionChange() == Offset.Zero) continue
                change.consume()
                finger = change.position
                extendTo(finger.y)
                val speed = edgeSpeed(finger.y, size.height.toFloat(), edge, maxSpeed)
                if (speed != 0f && autoScroll?.isActive != true) {
                    autoScroll = scope.launch {
                        while (isActive) {
                            val current = edgeSpeed(finger.y, size.height.toFloat(), edge, maxSpeed)
                            if (current == 0f) break
                            list.scrollBy(current)
                            extendTo(finger.y)
                            withFrameNanos { }
                        }
                    }
                }
            }
        } finally {
            autoScroll?.cancel()
            onEnd()
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitReleaseOrSlide(down: PointerInputChange) {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return
    }
}

private fun edgeSpeed(y: Float, height: Float, edge: Float, maxSpeed: Float): Float = when {
    y < edge -> -maxSpeed * (1f - (y / edge).coerceIn(0f, 1f))
    y > height - edge -> maxSpeed * (1f - ((height - y) / edge).coerceIn(0f, 1f))
    else -> 0f
}

private fun LazyListState.keyAt(y: Float): Any? {
    val contentY = (y + layoutInfo.viewportStartOffset).toInt()
    return layoutInfo.visibleItemsInfo.firstOrNull { contentY in it.offset until it.offset + it.size }?.key
}

private val AUTO_SCROLL_EDGE = 72.dp
private val AUTO_SCROLL_SPEED = 18.dp
