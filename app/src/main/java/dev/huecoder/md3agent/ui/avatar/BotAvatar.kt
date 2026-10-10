package dev.huecoder.md3agent.ui.avatar

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.atan2

@Composable
fun BotAvatar(
    isWorking: Boolean,
    restShape: AgentShape,
    size: Dp,
    modifier: Modifier = Modifier,
    workingShapes: List<AgentShape> = AgentProfile.DEFAULT_WORKING_SHAPES,
    bodyColor: Color = MaterialTheme.colorScheme.primary,
    eyeColor: Color = MaterialTheme.colorScheme.onPrimary,
    punchable: Boolean = false,
    typing: TypingAttention? = null,
    listeningLevel: Float? = null,
    knockout: KnockoutStage? = null,
    watchingBelow: Boolean = false,
) {
    val shapes = remember(restShape, workingShapes) { BotShapes.of(restShape, workingShapes) }
    val motion = remember(shapes) { BotMotion(shapes, startWorking = isWorking) }
    val punch = remember { BotPunch() }
    val face = remember { BotFace() }
    val listening = remember { BotListening() }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var centerInRoot by remember { mutableStateOf(Offset.Unspecified) }
    val density = LocalDensity.current
    val reach = with(density) { Offset(GAZE_REACH_X.toPx(), GAZE_REACH_Y.toPx()) }
    val pixelsPerMeter = with(density) { PHYSICS_METER.toPx() }
    val level by rememberUpdatedState(listeningLevel ?: 0f)

    LaunchedEffect(motion, isWorking) { if (isWorking) motion.work() else motion.rest() }
    LaunchedEffect(motion) { motion.blinkForever() }
    LaunchedEffect(face, punch, knockout, punchable) {
        if (!punchable && knockout == null) return@LaunchedEffect
        face.follow {
            when {
                knockout?.isDazed == true -> EyeGlyph.Cross
                punch.isSqueezing -> EyeGlyph.Squint
                else -> EyeGlyph.Plain
            }
        }
    }
    LaunchedEffect(motion, typing) {
        typing ?: return@LaunchedEffect
        motion.watchTyping(typing) { letter ->
            val center = centerInRoot.takeIf { it.isSpecified } ?: return@watchTyping null
            Offset(((letter.x - center.x) / reach.x).coerceIn(-1f, 1f), ((letter.y - center.y) / reach.y).coerceIn(-1f, 1f))
        }
    }
    val isListening = listeningLevel != null
    LaunchedEffect(motion, isListening, watchingBelow) {
        if (isListening) {
            motion.lookTo(LISTENING_GAZE, GazeSprings.Glance)
            listening.listen { level }
        } else {
            listening.stop()
            if (watchingBelow) motion.watchBelow() else motion.lookTo(Offset.Zero, GazeSprings.Home)
        }
    }

    val touch = if (punchable) {
        Modifier.pointerInput(punch) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val half = this.size.width / 2f
                val direction = (down.position - Offset(half, half)) / half
                val stage = knockout
                if (stage?.isPlaying == true) {
                    if (stage.isDazed) {
                        stage.hit(direction, power = 1f)
                        haptics.impact(1f)
                    }
                    return@awaitEachGesture
                }
                val landed = punch.hit(direction, SystemClock.uptimeMillis(), scope)
                scope.launch { motion.brace() }
                if (landed.isKnockout && stage != null && centerInRoot.isSpecified) {
                    punch.resetCombo()
                    haptics.thud()
                    scope.launch { stage.play(centerInRoot, half, direction, landed.power, pixelsPerMeter, onImpact = haptics::impact) }
                } else {
                    haptics.impact(landed.power)
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier
            .size(size)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.size
                centerInRoot = coordinates.positionInRoot() + Offset(bounds.width / 2f, bounds.height / 2f)
            }
            .graphicsLayer {
                knockout?.let { stage ->
                    translationX = stage.offset.x
                    translationY = stage.offset.y
                }
                rotationZ = listening.headTilt.value + (knockout?.spin ?: 0f)
                val swell = 1f + listening.swell.value
                scaleX = swell
                scaleY = swell
            }
            .then(touch),
    ) {
        val side = this.size.minDimension
        val recoil = punch.recoil.value * side
        translate(recoil.x, recoil.y) {
            squashed(punch.axis, punch.squash.value) {
                rotate(motion.rotation.value) { drawBody(motion, side, bodyColor) }
                val look = if (knockout?.isDazed == true) Offset.Zero else motion.gaze.value + punch.gazeKick.value
                val (left, right) = BotGeometry.eyes(
                    orientation = knockout?.orientation ?: Orientation.Identity,
                    blink = motion.blink.value,
                    safeRadius = shapes.safeRadius,
                    gaze = BotGeometry.gazeTowards(look.x, look.y),
                    wobble = motion.wobble,
                    glyph = face.glyph,
                    scale = face.scale.value,
                )
                listOfNotNull(left, right).forEach { drawPath(it.toPath(side, center.x, center.y), eyeColor) }
            }
        }
    }
}

private fun DrawScope.squashed(axis: Offset, amount: Float, draw: DrawScope.() -> Unit) {
    if (amount == 0f) return draw()
    val degrees = Math.toDegrees(atan2(axis.y, axis.x).toDouble()).toFloat()
    withTransform({
        rotate(degrees)
        scale(1f - amount, 1f + amount * BULGE)
        rotate(-degrees)
    }, draw)
}

private fun DrawScope.drawBody(motion: BotMotion, side: Float, color: Color) {
    val path = motion.morph.toPath(motion.progress.value)
    path.transform(Matrix().apply { scale(side, side) })
    drawPath(path, color)
}

private const val BULGE = 0.45f
private val PHYSICS_METER = 64.dp
private val LISTENING_GAZE = Offset(0f, -0.35f)
private val GAZE_REACH_X = 150.dp
private val GAZE_REACH_Y = 220.dp
