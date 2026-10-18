package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.ui.physics.Material

object SceneMotion {
    const val BASE_HEIGHT_SHARE = 0.55f
    const val METERS_ACROSS_BASE = 4f
    const val TOP_SIZE = 1.15f
    const val BOTTOM_SIZE = 1.35f
    val TOP_HOME = Offset(0.12f, 0.06f)
    val BOTTOM_CENTER = Offset(0.92f, 0.86f)

    const val STEP_SECONDS = 1.0 / 120.0
    const val GRAVITY = 12.0
    const val JUMP_METERS_PER_SECOND = 5.5f
    const val JUMP_SPREAD = 0.25f
    const val JUMP_SPIN = 0.8f
    const val QUIET_METERS_PER_SECOND = 0.35f
    const val QUIET_HOLD_SECONDS = 0.15f
    const val SETTLED_HIT_METERS_PER_SECOND = 2f
    const val SPIN_DAMPING = 0.8

    val BounceMaterial = Material(restitution = 0.82, friction = 0.3, density = 1.0)

    const val SQUASH_REFERENCE_SPEED = 9f
    const val SQUASH_BULGE = 0.5f
    const val SQUASH_STIFFNESS = 420f
    const val SQUASH_DRAG = 15.6f
    const val SQUASH_KICK = 3f
    const val BOTTOM_RECOIL_SHARE = 0.35f
    const val MIN_HIT_POWER = 0.12f

    const val TOP_STROKE_DP = 3f
    const val BOTTOM_STROKE_DP = 1.75f
    const val BOTTOM_ALPHA = 0.55f
    const val SCENERY_STROKE_DP = 1.25f
    const val SCENERY_ALPHA = 0.26f
    const val SCENERY_TURN_PER_PAGE = 0.35f
}
