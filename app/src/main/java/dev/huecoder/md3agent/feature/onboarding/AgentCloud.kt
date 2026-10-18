package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.avatar.AgentLook
import dev.huecoder.md3agent.ui.components.AgentAvatar
import dev.huecoder.md3agent.ui.components.boldColors
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private class Slot(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Int)

private val SLOTS = listOf(
    Slot(0.50f, 0.15f, 0.27f, 0.0f, 1),
    Slot(0.84f, 0.42f, 0.30f, 1.3f, 1),
    Slot(0.68f, 0.84f, 0.26f, 2.4f, 2),
    Slot(0.30f, 0.82f, 0.29f, 3.5f, 1),
    Slot(0.15f, 0.40f, 0.25f, 4.7f, 2),
)

@Composable
fun AgentCloud(
    looks: List<AgentLook>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    interactive: Boolean = false,
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val drift = rememberInfiniteTransition(label = "cloud-drift")
    val turn by drift.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(DRIFT_MILLIS, easing = LinearEasing)), label = "cloud-turn")
    val breath by drift.animateFloat(
        BREATH_MIN,
        BREATH_MAX,
        infiniteRepeatable(tween(BREATH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cloud-breath",
    )
    val focus by animateFloatAsState(if (selected != null) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 200f), label = "cloud-focus")
    val glow by animateColorAsState(selected?.let { looks.getOrNull(it)?.tint?.boldColors()?.container } ?: colors.primary, label = "cloud-glow")

    BoxWithConstraints(modifier) {
        val side = minOf(maxWidth, maxHeight)
        val sidePx = with(density) { side.toPx() }
        Canvas(Modifier.size(side)) {
            val radius = sidePx * GLOW_RADIUS * breath
            drawCircle(
                brush = Brush.radialGradient(listOf(glow.copy(alpha = GLOW_ALPHA), Color.Transparent), center, radius),
                radius = radius,
                center = center,
            )
        }
        looks.take(SLOTS.size).forEachIndexed { index, look ->
            val slot = SLOTS[index]
            val chosen by animateFloatAsState(if (selected == index) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 240f), label = "cloud-chosen")
            val away by animateFloatAsState(if (selected != null && selected != index) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 220f), label = "cloud-away")
            val avatarSize = side * slot.size
            val label = stringResource(R.string.onboarding_agent_option, index + 1)
            val pull = Offset(0.5f - slot.x, 0.5f - slot.y)
            val gaze = pull.let { Offset(it.x, it.y) / maxOf(it.getDistance(), MIN_PULL) }
            Box(
                Modifier
                    .offset { IntOffset(((slot.x - slot.size / 2f) * sidePx).roundToInt(), ((slot.y - slot.size / 2f) * sidePx).roundToInt()) }
                    .size(avatarSize)
                    .graphicsLayer {
                        val sway = sin(turn * slot.speed + slot.phase)
                        val bob = sin(turn * slot.speed + slot.phase * BOB_SKEW)
                        translationX = (pull.x * chosen - pull.x * away * SCATTER) * sidePx
                        translationY = (pull.y * chosen - pull.y * away * SCATTER) * sidePx + sway * BOB_DP.dp.toPx()
                        rotationZ = bob * TILT_DEGREES
                        val grow = 1f + chosen * (SELECTED_SCALE - 1f) - away * AWAY_SHRINK
                        scaleX = grow
                        scaleY = grow
                        alpha = 1f - away * AWAY_FADE
                    }
                    .semantics { contentDescription = label }
                    .then(if (onSelect != null) Modifier.clip(MaterialTheme.shapes.large).clickable { haptics.tick(); onSelect(index) } else Modifier),
            ) {
                AgentAvatar(
                    isWorking = false,
                    size = avatarSize,
                    look = look,
                    interactive = interactive,
                    lookAt = if (selected == index) Offset.Zero else gaze * LOOK_REACH,
                )
            }
        }
    }
}

private const val TWO_PI = (2 * PI).toFloat()
private const val DRIFT_MILLIS = 9_000
private const val BREATH_MILLIS = 3_200
private const val BREATH_MIN = 0.92f
private const val BREATH_MAX = 1.08f
private const val GLOW_RADIUS = 0.62f
private const val GLOW_ALPHA = 0.42f
private const val MIN_PULL = 0.001f
private const val LOOK_REACH = 0.85f
private const val BOB_SKEW = 1.3f
private const val BOB_DP = 6f
private const val TILT_DEGREES = 4f
private const val SCATTER = 0.9f
private const val SELECTED_SCALE = 1.55f
private const val AWAY_SHRINK = 0.25f
private const val AWAY_FADE = 0.55f
