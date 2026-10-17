package dev.huecoder.md3agent.assistant.glow

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.huecoder.md3agent.data.settings.AssistantGlow

@Composable
fun EdgeGlow(
    glow: AssistantGlow,
    grid: Boolean,
    thickness: Float,
    durationSeconds: Float,
    play: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    closing: Boolean = false,
    sweep: Boolean = false,
    onFinished: () -> Unit = {},
) {
    if (glow == AssistantGlow.OFF || !play) return
    val envelope = remember(durationSeconds, sweep) { if (sweep) GlowEnvelope.sweeping(durationSeconds) else GlowEnvelope.spanning(durationSeconds) }
    val accent = MaterialTheme.colorScheme.primary
    val stops = remember(glow, accent) { GlowPalette.stops(glow, accent) }
    val look = remember(glow, grid, thickness) { GlowLook.of(glow, grid, thickness) }
    val displayCorner = cornerRadius ?: rememberDisplayCornerRadius()
    var seconds by remember { mutableFloatStateOf(0f) }
    var finished by remember { mutableStateOf(false) }
    val closeFade = remember { Animatable(1f) }
    LaunchedEffect(envelope) {
        val start = withFrameNanos { it }
        while (seconds < envelope.durationSeconds && closeFade.value > 0f) {
            seconds = (withFrameNanos { it } - start) / NANOS_PER_SECOND
        }
        finished = true
    }
    LaunchedEffect(closing) {
        if (closing) closeFade.animateTo(0f, tween(CLOSE_FADE_MILLIS))
    }
    LaunchedEffect(finished) {
        if (finished) onFinished()
    }
    if (finished) return
    val pixelsPerDp = LocalDensity.current.density
    Box(
        modifier.fillMaxSize().drawWithCache {
            val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { GlowShader().also { it.configure(look, stops, size, pixelsPerDp, displayCorner.value) } }.getOrNull()
            } else {
                null
            }
            onDrawBehind {
                val level = envelope.at(seconds) * closeFade.value
                if (shader != null) {
                    shader.advance(seconds, level, band = if (sweep) sweepBand(seconds / envelope.durationSeconds) else null)
                    drawRect(shader.brush)
                } else {
                    drawLegacyGlow(stops, level, look.thickness, displayCorner.toPx())
                }
            }
        },
    )
}

@Composable
private fun rememberDisplayCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val pixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) displayCornerPixels(view) else 0
        with(density) { pixels.toDp() }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun displayCornerPixels(view: View): Int =
    view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0

private fun sweepBand(progress: Float): Float {
    val eased = progress.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    return lerp(SWEEP_FROM, SWEEP_TO, eased)
}

private fun DrawScope.drawLegacyGlow(stops: List<Color>, level: Float, thickness: Float, cornerRadius: Float) {
    val ring = Brush.sweepGradient(stops + stops.first(), center)
    for (layer in 0 until LEGACY_LAYERS) {
        val fraction = layer / (LEGACY_LAYERS - 1f)
        val width = (lerp(LEGACY_OUTER_WIDTH, LEGACY_INNER_WIDTH, fraction) * thickness).dp.toPx()
        drawRoundRect(
            brush = ring,
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius((cornerRadius - width / 2).coerceAtLeast(0f)),
            style = Stroke(width),
            alpha = level * LEGACY_PEAK_ALPHA * (LEGACY_FLOOR + (1f - LEGACY_FLOOR) * fraction),
        )
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val CLOSE_FADE_MILLIS = 450
private const val SWEEP_FROM = -0.2f
private const val SWEEP_TO = 1.2f
private const val LEGACY_LAYERS = 6
private const val LEGACY_OUTER_WIDTH = 40f
private const val LEGACY_INNER_WIDTH = 3f
private const val LEGACY_PEAK_ALPHA = 0.5f
private const val LEGACY_FLOOR = 0.15f
