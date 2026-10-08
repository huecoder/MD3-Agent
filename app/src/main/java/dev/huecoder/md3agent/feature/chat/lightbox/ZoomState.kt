package dev.huecoder.md3agent.feature.chat.lightbox

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
class ZoomState(private val scope: CoroutineScope) {
    var scale by mutableFloatStateOf(MIN_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    private var viewport = IntSize.Zero
    private var animation: Job? = null

    val isZoomed get() = scale > MIN_SCALE + ZOOM_EPSILON

    internal fun onViewport(size: IntSize) {
        viewport = size
    }

    internal fun transformBy(zoom: Float, pan: Offset, centroid: Offset) {
        animation?.cancel()
        val target = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val focus = centroid - viewportCenter()
        val moved = (offset - focus) * (target / scale) + focus + pan
        scale = target
        offset = clamp(moved, target)
    }

    internal fun toggleAt(tap: Offset) {
        if (isZoomed) {
            animateTo(MIN_SCALE, Offset.Zero)
        } else {
            animateTo(DOUBLE_TAP_SCALE, clamp((viewportCenter() - tap) * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE))
        }
    }

    fun reset() {
        animation?.cancel()
        scale = MIN_SCALE
        offset = Offset.Zero
    }

    private fun animateTo(targetScale: Float, targetOffset: Offset) {
        animation?.cancel()
        val startScale = scale
        val startOffset = offset
        animation = scope.launch {
            animate(0f, 1f) { progress, _ ->
                scale = startScale + (targetScale - startScale) * progress
                offset = lerp(startOffset, targetOffset, progress)
            }
        }
    }

    private fun viewportCenter() = Offset(viewport.width / 2f, viewport.height / 2f)

    private fun clamp(value: Offset, scale: Float): Offset {
        val limitX = viewport.width * (scale - 1f) / 2f
        val limitY = viewport.height * (scale - 1f) / 2f
        return Offset(value.x.coerceIn(-limitX, limitX), value.y.coerceIn(-limitY, limitY))
    }

    private companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
        const val ZOOM_EPSILON = 0.01f
    }
}

@Composable
fun rememberZoomState(): ZoomState {
    val scope = rememberCoroutineScope()
    return remember { ZoomState(scope) }
}

fun Modifier.zoomable(state: ZoomState, onTap: () -> Unit): Modifier = this
    .onSizeChanged(state::onViewport)
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed > 1 || state.isZoomed) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (centroid != Offset.Unspecified) state.transformBy(event.calculateZoom(), event.calculatePan(), centroid)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(state) { detectTapGestures(onTap = { onTap() }, onDoubleTap = state::toggleAt) }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
    }
