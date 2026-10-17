package dev.huecoder.md3agent.feature.phoneuse

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.huecoder.md3agent.assistant.glow.rememberDisplayCornerRadius

@Composable
fun ScanSweep(modifier: Modifier = Modifier, cornerRadius: Dp? = null, onFinished: () -> Unit = {}) {
    val displayCorner = cornerRadius ?: rememberDisplayCornerRadius()
    var progress by remember { mutableFloatStateOf(0f) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (progress < 1f) {
            progress = ((withFrameNanos { it } - start) / NANOS_PER_MILLI / SWEEP_MILLIS).coerceAtMost(1f)
        }
        finished = true
        onFinished()
    }
    if (finished) return
    val pixelsPerDp = LocalDensity.current.density
    Box(
        modifier.fillMaxSize().drawWithCache {
            val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { ScanShader().also { it.configure(size, pixelsPerDp, displayCorner.value) } }.getOrNull()
            } else {
                null
            }
            onDrawBehind {
                if (shader != null) {
                    shader.advance(progress, progress * SWEEP_MILLIS / MILLIS_PER_SECOND)
                    drawRect(shader.brush)
                } else {
                    drawFallbackBeam(progress)
                }
            }
        },
    )
}

private fun DrawScope.drawFallbackBeam(progress: Float) {
    val travel = size.height * (1f + 2f * FALLBACK_OVERSHOOT)
    val center = -size.height * FALLBACK_OVERSHOOT + travel * progress
    val half = size.height * FALLBACK_HALF_HEIGHT_SHARE
    val fade = (1f - progress).coerceIn(0f, 1f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = FALLBACK_PEAK_ALPHA * fade), Color.Transparent),
            startY = center - half,
            endY = center + half,
        ),
        topLeft = Offset(0f, center - half),
        size = Size(size.width, half * 2f),
    )
}

private const val SWEEP_MILLIS = 1_900f
private const val MILLIS_PER_SECOND = 1_000f
private const val NANOS_PER_MILLI = 1_000_000f
private const val FALLBACK_OVERSHOOT = 0.2f
private const val FALLBACK_HALF_HEIGHT_SHARE = 0.08f
private const val FALLBACK_PEAK_ALPHA = 0.45f
