package dev.huecoder.md3agent.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.random.Random

private data class Flinch(val direction: Offset, val power: Float)

@Stable
class BotMotion(private val shapes: BotShapes, startWorking: Boolean) {
    private enum class Phase { RESTING, WAKING, STEPPING, SETTLING }

    private var phase = if (startWorking) Phase.STEPPING else Phase.RESTING
    private var shapeIndex = 0
    private var endIndex = if (startWorking) 1 else 0

    var morph: Morph by mutableStateOf(if (startWorking) shapes.step(0) else shapes.still())
        private set
    val progress = Animatable(0f)
    val rotation = Animatable(0f)
    val gaze = Animatable(Offset.Zero, Offset.VectorConverter)
    val blink = Animatable(0f)
    private val flinches = Channel<Flinch>(Channel.CONFLATED)

    val wobble: Float
        get() = (gaze.velocity.getDistance() / WOBBLE_SPEED).coerceIn(0f, 1f)

    suspend fun work() = coroutineScope {
        launch { lookAround() }
        finishCurrent()
        if (phase == Phase.RESTING || phase == Phase.SETTLING) {
            phase = Phase.WAKING
            morph = shapes.wake()
            endIndex = 0
            progress.snapTo(0f)
            launch { rotation.animateTo(rotation.value + STEP_TURN, TURN_SPEC) }
            progress.animateTo(1f, MORPH_SPEC)
            shapeIndex = endIndex
        }
        phase = Phase.STEPPING
        while (true) {
            morph = shapes.step(shapeIndex)
            endIndex = (shapeIndex + 1) % shapes.working.size
            progress.snapTo(0f)
            launch { rotation.animateTo(rotation.value + STEP_TURN, TURN_SPEC) }
            progress.animateTo(1f, MORPH_SPEC)
            shapeIndex = endIndex
            delay(STEP_HOLD_MS)
        }
    }

    suspend fun rest() = coroutineScope {
        launch { lookTo(Offset.Zero, GazeSprings.Home) }
        if (phase == Phase.RESTING) return@coroutineScope
        finishCurrent()
        if (phase == Phase.STEPPING || phase == Phase.WAKING) {
            phase = Phase.SETTLING
            morph = shapes.settle(shapeIndex)
            progress.snapTo(0f)
            launch { rotation.animateTo(settledRotation(), SETTLE_SPEC) }
            progress.animateTo(1f, SETTLE_SPEC)
        }
        phase = Phase.RESTING
        morph = shapes.still()
        progress.snapTo(0f)
    }

    fun flinch(direction: Offset, power: Float) {
        flinches.trySend(Flinch(direction, power))
    }

    suspend fun lookTo(target: Offset, spec: AnimationSpec<Offset>) {
        try {
            gaze.animateTo(target, spec)
        } catch (interrupted: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }

    suspend fun blinkForever() {
        while (true) {
            val flinch = withTimeoutOrNull(Random.nextLong(BLINK_MIN_GAP_MS, BLINK_MAX_GAP_MS)) { flinches.receive() }
            if (flinch != null) {
                reactTo(flinch)
                continue
            }
            blinkOnce()
            if (Random.nextFloat() < DOUBLE_BLINK_CHANCE) {
                delay(DOUBLE_BLINK_GAP_MS)
                blinkOnce()
            }
        }
    }

    private suspend fun reactTo(flinch: Flinch) = coroutineScope {
        launch {
            val away = -flinch.direction * (FLINCH_MIN_REACH + (1f - FLINCH_MIN_REACH) * flinch.power)
            lookTo(Offset(away.x.coerceIn(-1f, 1f), away.y.coerceIn(-1f, 1f)), GazeSprings.Saccade)
            delay(FLINCH_HOLD_MS)
            lookTo(Offset.Zero, GazeSprings.Home)
        }
        blink.animateTo(FLINCH_SQUEEZE * (FLINCH_MIN_SQUEEZE + (1f - FLINCH_MIN_SQUEEZE) * flinch.power), tween(BLINK_CLOSE_MS))
        blink.animateTo(0f, FLINCH_OPEN_SPEC)
    }

    private suspend fun blinkOnce() {
        blink.animateTo(1f, tween(BLINK_CLOSE_MS))
        blink.animateTo(0f, tween(BLINK_OPEN_MS))
    }

    private suspend fun lookAround() {
        while (true) {
            val quick = Random.nextFloat() < SACCADE_CHANCE
            val target = Offset(Random.nextFloat() * 2f - 1f, (Random.nextFloat() * 2f - 1f) * VERTICAL_RANGE)
            lookTo(target, if (quick) GazeSprings.Saccade else GazeSprings.Glance)
            delay(Random.nextLong(GAZE_HOLD_MIN_MS, GAZE_HOLD_MAX_MS))
        }
    }

    private suspend fun finishCurrent() {
        if (phase != Phase.RESTING && progress.value < 1f) progress.animateTo(1f, FINISH_SPEC)
        if (phase == Phase.STEPPING || phase == Phase.WAKING) shapeIndex = endIndex
    }

    private fun settledRotation(): Float {
        val symmetry = shapes.restSymmetryDegrees
        if (symmetry == 0f) return rotation.value
        return (rotation.value / symmetry).roundToInt() * symmetry
    }

    private companion object {
        const val STEP_TURN = 90f
        const val STEP_HOLD_MS = 120L
        const val WOBBLE_SPEED = 6f
        const val VERTICAL_RANGE = 0.8f
        const val SACCADE_CHANCE = 0.3f
        const val GAZE_HOLD_MIN_MS = 300L
        const val GAZE_HOLD_MAX_MS = 1_400L
        const val BLINK_MIN_GAP_MS = 2_200L
        const val BLINK_MAX_GAP_MS = 5_200L
        const val BLINK_CLOSE_MS = 70
        const val BLINK_OPEN_MS = 120
        const val DOUBLE_BLINK_CHANCE = 0.2f
        const val DOUBLE_BLINK_GAP_MS = 140L
        const val FLINCH_MIN_REACH = 0.45f
        const val FLINCH_HOLD_MS = 260L
        const val FLINCH_SQUEEZE = 0.85f
        const val FLINCH_MIN_SQUEEZE = 0.4f
        val MORPH_SPEC = spring<Float>(dampingRatio = 0.6f, stiffness = 260f)
        val TURN_SPEC = spring<Float>(dampingRatio = 0.7f, stiffness = 120f)
        val FINISH_SPEC = spring<Float>(stiffness = Spring.StiffnessMedium)
        val SETTLE_SPEC = spring<Float>(dampingRatio = 0.75f, stiffness = 180f)
        val FLINCH_OPEN_SPEC = spring<Float>(dampingRatio = 0.6f, stiffness = 260f)
    }
}
