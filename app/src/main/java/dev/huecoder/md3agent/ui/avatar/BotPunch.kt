package dev.huecoder.md3agent.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

data class Punch(val direction: Offset, val power: Float, val combo: Int) {
    val isKnockout: Boolean get() = combo >= BotPunch.KNOCKOUT_COMBO
}

@Stable
class BotPunch {
    val gazeKick = Animatable(Offset.Zero, Offset.VectorConverter)
    val recoil = Animatable(Offset.Zero, Offset.VectorConverter)
    val squash = Animatable(0f)
    var isSqueezing: Boolean by mutableStateOf(false)
        private set
    var axis: Offset = Offset(1f, 0f)
        private set

    private var combo = 0
    private var lastHitAt = Long.MIN_VALUE
    private var gazeJob: Job? = null
    private var squeezeJob: Job? = null

    fun hit(direction: Offset, nowMs: Long, scope: CoroutineScope): Punch {
        val interval = nowMs - lastHitAt
        combo = if (interval <= COMBO_WINDOW_MS) combo + 1 else 1
        lastHitAt = nowMs
        val power = powerFor(combo, tempoOf(interval))
        val reach = direction.getDistance().coerceIn(0f, 1f)
        if (reach > AXIS_EPSILON) axis = direction / direction.getDistance()
        val share = MIN_REACH_SHARE + (1f - MIN_REACH_SHARE) * reach
        kickGaze(-axis * (GAZE_KICK_PER_HIT * power * share), scope)
        squeezeEyes(power, scope)
        val recoilKick = -axis * (MAX_RECOIL * power * share)
        scope.launch { recoil.animateTo(Offset.Zero, RECOIL_SPEC, recoil.velocity + impulse(recoilKick, RECOIL_STIFFNESS, RECOIL_DAMPING)) }
        scope.launch {
            val kick = impulse(MAX_SQUASH * power, SQUASH_STIFFNESS, SQUASH_DAMPING)
            squash.animateTo(0f, SQUASH_SPEC, squash.velocity + kick)
        }
        return Punch(direction, power, combo)
    }

    fun resetCombo() {
        combo = 0
    }

    private fun kickGaze(kick: Offset, scope: CoroutineScope) {
        gazeJob?.cancel()
        gazeJob = scope.launch {
            val target = (gazeKick.value + kick).limitedTo(MAX_GAZE_KICK)
            gazeKick.animateTo(target, tween(GAZE_KICK_MS, easing = SnapOut))
            gazeKick.animateTo(Offset.Zero, GAZE_RETURN_SPEC)
        }
    }

    private fun squeezeEyes(power: Float, scope: CoroutineScope) {
        squeezeJob?.cancel()
        isSqueezing = true
        squeezeJob = scope.launch {
            delay(SQUEEZE_HOLD_MS + (SQUEEZE_EXTRA_HOLD_MS * power).toLong())
            isSqueezing = false
        }
    }

    companion object {
        const val KNOCKOUT_COMBO = 30
        private const val COMBO_WINDOW_MS = 450L
        private const val FASTEST_INTERVAL_MS = 90L
        private const val BASE_POWER = 0.35f
        private const val COMBO_RAMP = 22f
        private const val TEMPO_BOOST = 0.8f
        private const val GAZE_KICK_PER_HIT = 0.9f
        private const val MAX_GAZE_KICK = 1f
        private const val GAZE_KICK_MS = 70
        private const val SQUEEZE_HOLD_MS = 220L
        private const val SQUEEZE_EXTRA_HOLD_MS = 200f
        private const val MAX_RECOIL = 0.09f
        private const val MIN_REACH_SHARE = 0.4f
        private const val MAX_SQUASH = 0.16f
        private const val AXIS_EPSILON = 0.05f
        private const val RECOIL_STIFFNESS = 520f
        private const val RECOIL_DAMPING = 0.5f
        private const val SQUASH_STIFFNESS = 700f
        private const val SQUASH_DAMPING = 0.38f
        private val SnapOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        private val GAZE_RETURN_SPEC = spring<Offset>(dampingRatio = 0.45f, stiffness = 260f)
        private val RECOIL_SPEC = spring<Offset>(dampingRatio = RECOIL_DAMPING, stiffness = RECOIL_STIFFNESS)
        private val SQUASH_SPEC = spring<Float>(dampingRatio = SQUASH_DAMPING, stiffness = SQUASH_STIFFNESS)

        internal fun powerFor(combo: Int, tempo: Float): Float {
            val ramp = BASE_POWER + (1f - BASE_POWER) * min(1f, (combo - 1) / COMBO_RAMP)
            return min(1f, ramp * (1f + TEMPO_BOOST * tempo))
        }

        internal fun tempoOf(intervalMs: Long): Float {
            if (intervalMs > COMBO_WINDOW_MS) return 0f
            return 1f - ((intervalMs - FASTEST_INTERVAL_MS).toFloat() / (COMBO_WINDOW_MS - FASTEST_INTERVAL_MS)).coerceIn(0f, 1f)
        }

        internal fun impulse(peak: Float, stiffness: Float, damping: Float): Float = peak * sqrt(stiffness) / firstSwing(damping)

        private fun impulse(peak: Offset, stiffness: Float, damping: Float): Offset =
            peak * (sqrt(stiffness) / firstSwing(damping))

        private fun firstSwing(damping: Float): Float {
            val damped = sqrt(1f - damping * damping)
            return exp(-damping / damped * atan(damped / damping))
        }

        private fun Offset.limitedTo(maxLength: Float): Offset {
            val length = getDistance()
            return if (length > maxLength) this * (maxLength / length) else this
        }
    }
}
