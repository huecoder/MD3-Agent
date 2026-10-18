package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.ui.physics.Material
import dev.huecoder.md3agent.ui.physics.Spring

object SceneMotion {
    const val BASE_HEIGHT_SHARE = 0.55f
    const val METERS_ACROSS_BASE = 4f
    const val TOP_SIZE = 1.15f
    const val BOTTOM_SIZE = 1.35f
    val TOP_HOME = Offset(0.12f, 0.06f)
    val BOTTOM_CENTER = Offset(0.92f, 0.86f)

    const val STEP_SECONDS = 1.0 / 120.0
    const val LAUNCH_METERS_PER_SECOND = 7f
    const val LAUNCH_SPIN = 0.5f
    const val STRIKE_TIMEOUT_SECONDS = 1.2f
    const val RETURN_TIMEOUT_SECONDS = 4f
    const val SETTLE_DISTANCE_SHARE = 0.004f
    const val SETTLE_METERS_PER_SECOND = 0.05f
    const val SETTLE_SPIN = 0.08f
    const val SETTLE_HOLD_SECONDS = 0.1f
    const val ANGLE_GAIN = 6.0
    const val MAX_SPIN_ACCELERATION = 40.0

    val TopMaterial = Material(restitution = 0.55, friction = 0.25, density = 1.0)
    val BottomMaterial = Material(restitution = 0.55, friction = 0.25, density = 1.0)
    val ReturnSpring = Spring(frequency = 1.6, damping = 0.4, maxForcePerKilogram = 90.0)

    const val SQUASH_REFERENCE_SPEED = 2_400f
    const val MAX_SQUASH = 0.1f
    const val SQUASH_BULGE = 0.5f
    const val SQUASH_STIFFNESS = 420f
    const val SQUASH_DRAG = 15.6f
    const val SQUASH_KICK = 3f
    const val BOTTOM_RECOIL_SHARE = 0.35f
    const val MIN_HIT_POWER = 0.12f

    const val TOP_STROKE_DP = 3f
    const val BOTTOM_STROKE_DP = 1.75f
    const val BOTTOM_ALPHA = 0.55f
    const val IDLE_STRIKE_MILLIS = 3_400L
}
