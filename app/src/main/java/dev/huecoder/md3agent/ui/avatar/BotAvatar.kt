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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.data.settings.AvatarRestShape
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BotAvatar(
    isWorking: Boolean,
    restShape: AvatarRestShape,
    size: Dp,
    modifier: Modifier = Modifier,
    bodyColor: Color = MaterialTheme.colorScheme.primary,
    eyeColor: Color = MaterialTheme.colorScheme.onPrimary,
    punchable: Boolean = false,
    typing: TypingAttention? = null,
    listeningLevel: Float? = null,
    knockout: KnockoutStage? = null,
    onPunch: (Punch) -> Unit = {},
) {
    val shapes = remember(restShape) { BotShapes.of(restShape) }
    val motion = remember(shapes) { BotMotion(shapes, startWorking = isWorking) }
    val punch = remember { BotPunch() }
    val listening = remember { BotListening() }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var centerInRoot by remember { mutableStateOf(Offset.Unspecified) }
    val density = LocalDensity.current
    val reach = with(density) { Offset(GAZE_REACH_X.toPx(), GAZE_REACH_Y.toPx()) }
    val pixelsPerMeter = with(density) { PHYSICS_METER.toPx() }
    val level by rememberUpdatedState(listeningLevel ?: 0f)
    val currentOnPunch by rememberUpdatedState(onPunch)

    LaunchedEffect(motion, isWorking) { if (isWorking) motion.work() else motion.rest() }
    LaunchedEffect(motion) { motion.blinkForever() }
    LaunchedEffect(motion, typing) {
        typing ?: return@LaunchedEffect
        motion.watchTyping(typing) { letter ->
            val center = centerInRoot.takeIf { it.isSpecified } ?: return@watchTyping null
            Offset(((letter.x - center.x) / reach.x).coerceIn(-1f, 1f), ((letter.y - center.y) / reach.y).coerceIn(-1f, 1f))
        }
    }
    val isListening = listeningLevel != null
    LaunchedEffect(motion, isListening) {
        if (isListening) {
            motion.lookTo(LISTENING_GAZE, GazeSprings.Glance)
            listening.listen { level }
        } else {
            listening.stop()
            motion.lookTo(Offset.Zero, GazeSprings.Home)
        }
    }

    val touch = if (punchable) {
        Modifier.pointerInput(punch) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                if (knockout?.isPlaying == true) return@awaitEachGesture
                val half = this.size.width / 2f
                val direction = (down.position - Offset(half, half)) / half
                val landed = punch.hit(direction, SystemClock.uptimeMillis(), scope)
                val distance = direction.getDistance()
                val unit = if (distance > 0f) direction / distance else Offset.Zero
                motion.flinch(unit, landed.power)
                currentOnPunch(landed)
                if (landed.isKnockout && knockout != null && centerInRoot.isSpecified) {
                    punch.resetCombo()
                    haptics.thud()
                    scope.launch { knockout.play(centerInRoot, half, away = -unit, pixelsPerMeter, onImpact = haptics::impact) }
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
                turned(punch.tilt.value, side) {
                    rotate(motion.rotation.value) { drawBody(motion, side, bodyColor) }
                    val look = motion.gaze.value
                    val gaze = BotGeometry.gazeTowards(look.x, look.y)
                    val head = punch.tilt.value * EYE_TURN
                    val turnedGaze = Gaze(gaze.yaw - head.x, gaze.pitch + head.y)
                    val (left, right) = BotGeometry.eyes(turnedGaze, motion.blink.value, motion.wobble, shapes.safeRadius)
                    if (knockout?.isDazed == true) {
                        drawDazedEye(left, side, eyeColor)
                        drawDazedEye(right, side, eyeColor)
                    } else {
                        drawEye(left, side, eyeColor)
                        drawEye(right, side, eyeColor)
                    }
                }
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

private fun DrawScope.turned(tilt: Offset, side: Float, draw: DrawScope.() -> Unit) {
    if (tilt == Offset.Zero) return draw()
    val matrix = android.graphics.Matrix()
    matrix.setPolyToPoly(squareCorners(side), 0, projectedCorners(side, tilt.x, tilt.y), 0, CORNERS)
    val left = center.x - side / 2f
    val top = center.y - side / 2f
    matrix.preTranslate(-left, -top)
    matrix.postTranslate(left, top)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.concat(matrix)
        draw()
        canvas.nativeCanvas.restore()
    }
}

private fun squareCorners(side: Float) = floatArrayOf(0f, 0f, side, 0f, side, side, 0f, side)

private fun projectedCorners(side: Float, yaw: Float, pitch: Float): FloatArray {
    val half = side / 2f
    val camera = CAMERA_DISTANCE * side
    val corners = squareCorners(side)
    return FloatArray(corners.size).also { projected ->
        for (corner in 0 until CORNERS) {
            val x = corners[corner * 2] - half
            val y = corners[corner * 2 + 1] - half
            val yawedX = x * cos(yaw)
            val yawedDepth = x * sin(yaw)
            val pitchedY = y * cos(pitch) - yawedDepth * sin(pitch)
            val depth = y * sin(pitch) + yawedDepth * cos(pitch)
            val perspective = camera / (camera + depth)
            projected[corner * 2] = half + yawedX * perspective
            projected[corner * 2 + 1] = half + pitchedY * perspective
        }
    }
}

private fun DrawScope.drawBody(motion: BotMotion, side: Float, color: Color) {
    val path = motion.morph.toPath(motion.progress.value)
    path.transform(Matrix().apply { scale(side, side) })
    drawPath(path, color)
}

private fun DrawScope.drawEye(eye: Eye, side: Float, color: Color) {
    val outline = Path()
    eye.xs.indices.forEach { index ->
        val x = center.x + eye.xs[index] * side
        val y = center.y + eye.ys[index] * side
        if (index == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
    }
    outline.close()
    drawPath(outline, color)
}

private fun DrawScope.drawDazedEye(eye: Eye, side: Float, color: Color) {
    val center = Offset(center.x + eye.centerX * side, center.y + eye.centerY * side)
    val arm = eye.width.coerceAtLeast(eye.height * DAZED_ASPECT) * side * DAZED_ARM
    val stroke = eye.width * side * DAZED_STROKE
    drawLine(color, center + Offset(-arm, -arm), center + Offset(arm, arm), stroke, StrokeCap.Round)
    drawLine(color, center + Offset(-arm, arm), center + Offset(arm, -arm), stroke, StrokeCap.Round)
}

private const val CORNERS = 4
private const val DAZED_ASPECT = 0.5f
private const val DAZED_ARM = 0.42f
private const val DAZED_STROKE = 0.42f
private val PHYSICS_METER = 64.dp
private const val CAMERA_DISTANCE = 2.2f
private const val BULGE = 0.45f
private const val EYE_TURN = 0.55f
private val LISTENING_GAZE = Offset(0f, -0.35f)
private val GAZE_REACH_X = 150.dp
private val GAZE_REACH_Y = 220.dp
