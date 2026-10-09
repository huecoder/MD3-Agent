package dev.huecoder.md3agent.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
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
    val tilt = Animatable(Offset.Zero, Offset.VectorConverter)
    val recoil = Animatable(Offset.Zero, Offset.VectorConverter)
    val squash = Animatable(0f)
    var axis: Offset = Offset(1f, 0f)
        private set

    private var combo = 0
    private var lastHitAt = Long.MIN_VALUE

    fun hit(direction: Offset, nowMs: Long, scope: CoroutineScope): Punch {
        combo = if (nowMs - lastHitAt <= COMBO_WINDOW_MS) combo + 1 else 1
        lastHitAt = nowMs
        val power = powerFor(combo)
        val reach = direction.getDistance().coerceIn(0f, 1f)
        if (reach > AXIS_EPSILON) axis = direction / direction.getDistance()
        val tiltKick = direction * (MAX_TILT * power)
        val recoilKick = -axis * (MAX_RECOIL * power * (MIN_RECOIL_SHARE + (1f - MIN_RECOIL_SHARE) * reach))
        scope.launch { tilt.animateTo(Offset.Zero, TILT_SPEC, tilt.velocity + impulse(tiltKick, TILT_STIFFNESS, TILT_DAMPING)) }
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

    companion object {
        const val KNOCKOUT_COMBO = 30
        private const val COMBO_WINDOW_MS = 450L
        private const val BASE_POWER = 0.35f
        private const val COMBO_RAMP = 22f
        private const val MAX_TILT = 0.75f
        private const val MAX_RECOIL = 0.09f
        private const val MIN_RECOIL_SHARE = 0.4f
        private const val MAX_SQUASH = 0.16f
        private const val AXIS_EPSILON = 0.05f
        private const val TILT_STIFFNESS = 420f
        private const val TILT_DAMPING = 0.42f
        private const val RECOIL_STIFFNESS = 520f
        private const val RECOIL_DAMPING = 0.5f
        private const val SQUASH_STIFFNESS = 700f
        private const val SQUASH_DAMPING = 0.38f
        private val TILT_SPEC = spring<Offset>(dampingRatio = TILT_DAMPING, stiffness = TILT_STIFFNESS)
        private val RECOIL_SPEC = spring<Offset>(dampingRatio = RECOIL_DAMPING, stiffness = RECOIL_STIFFNESS)
        private val SQUASH_SPEC = spring<Float>(dampingRatio = SQUASH_DAMPING, stiffness = SQUASH_STIFFNESS)

        fun powerFor(combo: Int): Float = BASE_POWER + (1f - BASE_POWER) * min(1f, (combo - 1) / COMBO_RAMP)

        fun impulse(peak: Float, stiffness: Float, damping: Float): Float = peak * sqrt(stiffness) / firstSwing(damping)

        private fun impulse(peak: Offset, stiffness: Float, damping: Float): Offset =
            peak * (sqrt(stiffness) / firstSwing(damping))

        private fun firstSwing(damping: Float): Float {
            val damped = sqrt(1f - damping * damping)
            return exp(-damping / damped * atan(damped / damping))
        }
    }
}
