package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

object OnboardingMotion {
    const val PARALLAX = 0.24f
    const val PAGE_SHRINK = 0.04f
    const val STAGGER = 0.12f
    val REVEAL_DISTANCE = 26.dp
    val PageSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
    val SnapSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 320f)
    const val SWIPE_COMMIT_SHARE = 0.18f
    const val LANGUAGE_PULL = 0.5f
    const val NUDGE_SHARE = 0.06f
    val ContentPadding = 24.dp
    val BarHeight = 56.dp
    const val DEPART_MILLIS = 480
}
