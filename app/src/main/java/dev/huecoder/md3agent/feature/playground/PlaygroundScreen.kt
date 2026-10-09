package dev.huecoder.md3agent.feature.playground

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.avatar.BotGeometry
import dev.huecoder.md3agent.ui.avatar.Eye
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlinx.coroutines.delay

@Composable
fun PlaygroundScreen() {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary
    val playground = remember(accent) {
        with(density) {
            Playground(
                pixelsPerMeter = PHYSICS_METER.toPx(),
                palette = PlaygroundPalette(accent),
                pieceSizes = MIN_PIECE.toPx()..MAX_PIECE.toPx(),
                onImpact = haptics::impact,
            )
        }
    }
    var frame by remember { mutableLongStateOf(0L) }
    var showHint by remember { mutableStateOf(true) }

    LaunchedEffect(playground) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            playground.step((now - last) / NANOS_PER_SECOND)
            last = now
            frame++
        }
    }
    LaunchedEffect(Unit) {
        delay(HINT_MS)
        showHint = false
    }
    TiltGravity(playground)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { playground.resize(it.toSize()) }
                .pointerInput(playground) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { change ->
                                val pointer = change.id.value
                                when {
                                    change.changedToDown() -> if (playground.grab(pointer, change.position)) haptics.tick()
                                    change.changedToUp() -> playground.release(pointer)
                                    change.pressed -> playground.drag(pointer, change.position)
                                }
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            if (frame >= 0) playground.pieces.forEach { piece -> drawPiece(playground, piece) }
        }
        AnimatedVisibility(
            visible = showHint,
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 24.dp),
        ) {
            Text(
                stringResource(R.string.playground_hint),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.drawPiece(playground: Playground, piece: Piece) {
    val origin = playground.origin(piece)
    val degrees = Math.toDegrees(playground.angle(piece).toDouble()).toFloat()
    translate(origin.x, origin.y) {
        rotate(degrees, pivot = Offset.Zero) {
            scale(piece.size, pivot = Offset.Zero) {
                drawPath(piece.shape.outline, piece.colors.body)
                val face = piece.face ?: return@scale
                val eyeRadius = piece.shape.eyeRadius ?: return@scale
                val gaze = BotGeometry.gazeTowards(face.gaze.x, face.gaze.y)
                val (left, right) = BotGeometry.eyes(gaze, face.blink, wobble = 0f, safeRadius = eyeRadius)
                drawPath(left.toPath(), piece.colors.eyes)
                drawPath(right.toPath(), piece.colors.eyes)
            }
        }
    }
}

private fun Eye.toPath(): Path = Path().apply {
    xs.indices.forEach { index -> if (index == 0) moveTo(xs[index], ys[index]) else lineTo(xs[index], ys[index]) }
    close()
}

@Composable
private fun TiltGravity(playground: Playground) {
    val context = LocalContext.current
    DisposableEffect(playground) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var smoothed = Offset(0f, 1f)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val screen = toScreenAxes(event.values[0], event.values[1], displayRotation(context))
                val pull = if (screen.getDistance() < FLAT_THRESHOLD) Offset(0f, 1f) else screen
                smoothed += (pull - smoothed) * TILT_SMOOTHING
                playground.pointGravity(smoothed)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensors != null && sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensors?.unregisterListener(listener) }
    }
}

private fun toScreenAxes(x: Float, y: Float, rotation: Int): Offset = when (rotation) {
    Surface.ROTATION_90 -> Offset(y, x)
    Surface.ROTATION_180 -> Offset(x, -y)
    Surface.ROTATION_270 -> Offset(-y, -x)
    else -> Offset(-x, y)
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0

private val PHYSICS_METER = 64.dp
private val MIN_PIECE = 22.dp
private val MAX_PIECE = 140.dp
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val HINT_MS = 3_500L
private const val FLAT_THRESHOLD = 3f
private const val TILT_SMOOTHING = 0.2f
