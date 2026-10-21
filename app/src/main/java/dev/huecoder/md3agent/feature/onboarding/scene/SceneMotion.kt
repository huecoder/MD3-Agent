package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.ui.physics.Material
import kotlin.math.sqrt

object SceneMotion {
    const val BASE_HEIGHT_SHARE = 0.55f
    const val METERS_ACROSS_BASE = 4f
    const val TOP_SIZE = 1.15f
    const val BOTTOM_SIZE = 1.35f
    val TOP_HOME = Offset(0.12f, 0.06f)
    val BOTTOM_CENTER = Offset(0.92f, 0.86f)

    const val STEP_SECONDS = 1.0 / 120.0
    const val GRAVITY = 12.0
    const val LAUNCH_METERS_PER_SECOND = 2f
    const val FIRST_REST_SECONDS = 0.9f
    const val REST_SECONDS = 1.5f
    const val RETURN_SECONDS = 1.1f

    val StrikeMaterial = Material(restitution = 0.0, friction = 0.0, density = 1.0)

    const val BOTTOM_TURN_RADIANS_PER_SECOND = 0.07f
    const val MORPH_SECONDS = 1.1f

    const val SQUASH_REFERENCE_SPEED = 9f
    const val SQUASH_BULGE = 0.5f
    const val SQUASH_STIFFNESS = 144f
    val SQUASH_DRAG = 2f * sqrt(SQUASH_STIFFNESS)
    const val SQUASH_KICK = 4.5f
    const val BOTTOM_RECOIL_SHARE = 0.35f

    const val TOP_STROKE_DP = 7f
    const val BOTTOM_STROKE_DP = 6f
    const val SCENERY_STROKE_DP = 4f
    const val TOP_ALPHA = 0.42f
    const val BOTTOM_ALPHA = 0.3f
    const val SCENERY_ALPHA = 0.16f
    const val SCENERY_TURN_PER_PAGE = 0.35f
}
