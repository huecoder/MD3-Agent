package com.mwagent.feature.phoneuse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mwagent.data.settings.AgentPlacement
import com.mwagent.data.settings.PhoneUseSettings
import com.mwagent.system.control.CursorState
import com.mwagent.ui.avatar.LocalAgentLook
import com.mwagent.ui.components.AgentAvatar
import com.mwagent.ui.components.boldColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CursorAcknowledgements(val onArrived: (Int) -> Unit, val onPressSettled: (Int) -> Unit)

@Composable
fun CursorLayer(cursor: CursorState, settings: PhoneUseSettings, acknowledgements: CursorAcknowledgements, modifier: Modifier = Modifier) {
    val colors = LocalAgentLook.current.tint.boldColors()
    val density = LocalDensity.current
    val sizePx = with(density) { CURSOR_SIZE.toPx() } * settings.cursorSize
    val screen = remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onSizeChanged { screen.value = Offset(it.width.toFloat(), it.height.toFloat()) }) {
        val position = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val pressScale = remember { Animatable(1f) }
        val ripple = remember { Animatable(1f) }
        val trail = remember { mutableStateListOf<Offset>() }
        val glyph = remember(sizePx, settings.cursorShape) { CursorGlyph.of(settings.cursorShape, sizePx) }
        var captionShown by remember { mutableStateOf(false) }
        var placed by remember { mutableStateOf(false) }
        val arrivalRadius = sizePx * ARRIVAL_RADIUS_SHARE

        LaunchedEffect(cursor.aimToken, screen.value) {
            if (screen.value == Offset.Zero) return@LaunchedEffect
            if (!placed) {
                position.snapTo(screen.value / 2f)
                placed = true
            }
            val target = if (cursor.x != null && cursor.y != null) Offset(cursor.x, cursor.y) else screen.value / 2f
            val token = cursor.aimToken
            val spec = cursor.travelMs?.let { tween<Offset>(it, easing = FastOutSlowInEasing) }
                ?: spring(dampingRatio = MOVE_DAMPING, stiffness = MOVE_STIFFNESS)
            coroutineScope {
                launch {
                    snapshotFlow { (position.value - target).getDistance() <= arrivalRadius }.first { it }
                    acknowledgements.onArrived(token)
                }
                position.animateTo(target, spec)
                acknowledgements.onArrived(token)
            }
        }
        LaunchedEffect(cursor.pressed, cursor.pressToken) {
            if (cursor.pressed) {
                pressScale.animateTo(CursorMotion.PRESSED_SCALE, tween(CursorMotion.PRESS_MS, easing = FastOutSlowInEasing))
                acknowledgements.onPressSettled(cursor.pressToken)
            } else if (cursor.pressToken > 0) {
                coroutineScope {
                    launch {
                        ripple.snapTo(0f)
                        ripple.animateTo(1f, tween(CursorMotion.RIPPLE_MS))
                    }
                    pressScale.animateTo(1f, spring(dampingRatio = CursorMotion.RELEASE_DAMPING, stiffness = CursorMotion.RELEASE_STIFFNESS))
                }
            }
        }
        LaunchedEffect(cursor.captionToken) {
            if (cursor.caption == null) return@LaunchedEffect
            captionShown = true
            delay(CAPTION_IDLE_MS)
            captionShown = false
        }
        LaunchedEffect(settings.cursorTrail) {
            trail.clear()
            if (!settings.cursorTrail) return@LaunchedEffect
            snapshotFlow { position.value }.collect {
                trail.add(it)
                while (trail.size > TRAIL_POINTS) trail.removeAt(0)
            }
        }
        LaunchedEffect(settings.cursorTrail) {
            while (settings.cursorTrail) {
                delay(TRAIL_DECAY_MS)
                if (trail.isNotEmpty()) trail.removeAt(0)
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            if (!placed) return@Canvas
            if (settings.cursorTrail) drawTrail(trail, colors.container, sizePx)
            drawRipple(position.value, ripple.value, colors.container, sizePx)
            drawCursor(position.value, glyph, pressScale.value, colors.container)
        }

        if (placed && settings.agentPlacement != AgentPlacement.HIDDEN) {
            val avatarPx = with(density) { AGENT_SIZE.toPx() } * settings.cursorSize
            val avatarOffset = if (settings.agentPlacement == AgentPlacement.ON_CURSOR) {
                Offset(-avatarPx / 2f + sizePx * ON_CURSOR_SHIFT, -avatarPx / 2f + sizePx * ON_CURSOR_SHIFT)
            } else {
                Offset(sizePx * BESIDE_SHIFT_X, sizePx * BESIDE_SHIFT_Y)
            }
            AgentAvatar(
                isWorking = !cursor.waiting,
                size = AGENT_SIZE * settings.cursorSize,
                modifier = Modifier.follow({ position.value }, avatarOffset, screen.value),
            )
        }

        val text = cursor.caption
        if (placed && settings.cursorCaption && !text.isNullOrBlank()) {
            val captionOffset = Offset(sizePx * CAPTION_SHIFT_X, sizePx * CAPTION_SHIFT_Y + with(density) { CAPTION_GAP.toPx() })
            AnimatedVisibility(
                visible = captionShown,
                enter = fadeIn(tween(CAPTION_FADE_MS)),
                exit = fadeOut(tween(CAPTION_FADE_MS)),
                modifier = Modifier.follow({ position.value }, captionOffset, screen.value, keepInside = true),
            ) {
                Caption(text, colors.container, colors.content)
            }
        }
    }
}

@Composable
private fun Caption(text: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.large, color = container, contentColor = content, modifier = Modifier.widthIn(max = CAPTION_MAX_WIDTH)) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(tween(CAPTION_FADE_MS)) togetherWith fadeOut(tween(CAPTION_FADE_MS)) },
            label = "cursor-caption",
        ) { shown ->
            Text(
                shown,
                style = MaterialTheme.typography.labelLarge,
                maxLines = CAPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun Modifier.follow(position: () -> Offset, shift: Offset, screen: Offset, keepInside: Boolean = false): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(placeable.width, placeable.height) {
            val anchor = position() + shift
            var x = anchor.x
            var y = anchor.y
            if (keepInside && screen != Offset.Zero) {
                val margin = SCREEN_MARGIN_PX
                if (x + placeable.width > screen.x - margin) x = screen.x - margin - placeable.width
                if (x < margin) x = margin
                if (y + placeable.height > screen.y - margin) y = position().y - placeable.height - margin * 2
            }
            placeable.place(IntOffset(x.roundToInt(), y.roundToInt()))
        }
    }

private fun DrawScope.drawTrail(points: List<Offset>, color: Color, base: Float) {
    if (points.size < 2) return
    points.zipWithNext().forEachIndexed { index, (from, to) ->
        val fade = (index + 1f) / points.size
        drawLine(color.copy(alpha = TRAIL_ALPHA * fade), from, to, strokeWidth = base * TRAIL_WIDTH * fade, cap = StrokeCap.Round)
    }
}

private val CURSOR_SIZE: Dp = 28.dp
private val AGENT_SIZE: Dp = 30.dp
private val CAPTION_GAP: Dp = 6.dp
private val CAPTION_MAX_WIDTH: Dp = 260.dp
private const val ARRIVAL_RADIUS_SHARE = 0.25f
private const val MOVE_DAMPING = 0.92f
private const val MOVE_STIFFNESS = 240f
private const val CAPTION_IDLE_MS = 4_000L
private const val CAPTION_FADE_MS = 180
private const val CAPTION_LINES = 3
private const val TRAIL_POINTS = 28
private const val TRAIL_DECAY_MS = 40L
private const val TRAIL_ALPHA = 0.5f
private const val TRAIL_WIDTH = 0.34f
private const val BESIDE_SHIFT_X = 0.95f
private const val BESIDE_SHIFT_Y = 0.55f
private const val ON_CURSOR_SHIFT = 0.45f
private const val CAPTION_SHIFT_X = 0.2f
private const val CAPTION_SHIFT_Y = 1.45f
private const val SCREEN_MARGIN_PX = 12f
