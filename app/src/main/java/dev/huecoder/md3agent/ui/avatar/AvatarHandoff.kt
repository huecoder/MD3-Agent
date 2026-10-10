package dev.huecoder.md3agent.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Stable
class AvatarHandoff {
    enum class Phase { IDLE, FADING, WAITING, JUMPING, LANDED }

    private val greeting = Animatable(1f)
    private val progress = Animatable(0f)
    private val squash = Animatable(0f)
    private var start: Rect? = null
    private var homeBounds: Rect? = null
    private var target: Rect? by mutableStateOf(null)

    var phase: Phase by mutableStateOf(Phase.IDLE)
        private set

    val isActive: Boolean get() = phase != Phase.IDLE
    val greetingAlpha: Float get() = greeting.value

    suspend fun play() {
        val from = homeBounds ?: return
        start = from
        greeting.snapTo(1f)
        progress.snapTo(0f)
        squash.snapTo(0f)
        phase = Phase.FADING
        try {
            greeting.animateTo(0f, tween(FADE_MS))
            phase = Phase.WAITING
            squash.animateTo(CROUCH, tween(CROUCH_MS))
            val landing = withTimeoutOrNull(TARGET_TIMEOUT_MS) { snapshotFlow { target }.filterNotNull().first() } ?: return
            if (landing.width <= 0f) return
            phase = Phase.JUMPING
            squash.animateTo(0f, tween(CROUCH_MS))
            progress.animateTo(1f, tween(JUMP_MS, easing = JumpEasing))
            phase = Phase.LANDED
            squash.animateTo(0f, LANDING_SPEC, initialVelocity = LANDING_KICK)
        } finally {
            reset()
        }
    }

    fun avatarModifier(): Modifier = Modifier
        .onGloballyPositioned { homeBounds = it.boundsInRoot() }
        .graphicsLayer { applyFlight() }

    fun targetModifier(): Modifier = Modifier.onGloballyPositioned { target = it.boundsInRoot() }

    private fun GraphicsLayerScope.applyFlight() {
        val from = start ?: return
        val to = target
        val travel = progress.value
        if (to != null && travel > 0f) {
            val arc = from.height * ARC_HEIGHTS * 4f * travel * (1f - travel)
            val center = lerp(from.center, to.center, travel) - Offset(0f, arc)
            translationX = center.x - from.center.x
            translationY = center.y - from.center.y
        }
        val shrink = if (to == null) 1f else lerp(1f, to.width / from.width, travel)
        val squeeze = squash.value
        scaleX = shrink * (1f + squeeze * SQUASH_SPREAD)
        scaleY = shrink * (1f - squeeze)
    }

    private fun reset() {
        phase = Phase.IDLE
        target = null
        start = null
    }

    private companion object {
        const val FADE_MS = 200
        const val CROUCH_MS = 110
        const val JUMP_MS = 560
        const val TARGET_TIMEOUT_MS = 2_500L
        const val CROUCH = 0.14f
        const val SQUASH_SPREAD = 0.6f
        const val ARC_HEIGHTS = 0.9f
        const val LANDING_KICK = 3.2f
        val JumpEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
        val LANDING_SPEC = spring<Float>(dampingRatio = 0.35f, stiffness = 650f)
    }
}
