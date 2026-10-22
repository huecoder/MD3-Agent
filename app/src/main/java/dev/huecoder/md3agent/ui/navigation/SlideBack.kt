package dev.huecoder.md3agent.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal val BackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

internal enum class SlideEdge { BOTTOM, START }

@Stable
internal class SlideBack {
    private val progress = Animatable(0f)

    val value: Float get() = progress.value

    suspend fun follow(events: Flow<BackEventCompat>) {
        events.collect { progress.snapTo(BackGestureEasing.transform(it.progress)) }
    }

    suspend fun release() {
        progress.animateTo(0f)
    }

    suspend fun reset() {
        progress.snapTo(0f)
    }
}

@Composable
internal fun rememberSlideBack(): SlideBack = remember { SlideBack() }

@Composable
internal fun SlideBackHandler(enabled: Boolean, back: SlideBack, onCommit: suspend () -> Unit) {
    PredictiveBackHandler(enabled) { events ->
        val committed = try {
            back.follow(events)
            true
        } catch (e: CancellationException) {
            false
        }
        if (committed) onCommit() else back.release()
    }
}

internal fun Modifier.slideBack(back: SlideBack, edge: SlideEdge, travel: Dp = SLIDE_TRAVEL): Modifier = graphicsLayer {
    val offset = travel.toPx() * back.value
    when (edge) {
        SlideEdge.BOTTOM -> translationY = offset
        SlideEdge.START -> translationX = -offset
    }
}

private val SLIDE_TRAVEL = 56.dp
