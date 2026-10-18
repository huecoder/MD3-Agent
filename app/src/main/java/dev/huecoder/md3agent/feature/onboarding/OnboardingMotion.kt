package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

object OnboardingMotion {
    const val STAGGER = 0.12f
    val TRAIL = 72.dp
    val PageSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
    val SnapSpring = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    const val SNAP_THRESHOLD = 0.4f
    const val SLOP_SCALE = 1.7f
    val FLING_MIN = 700.dp
    val NUDGE = 36.dp
    val ContentPadding = 24.dp
    val BarHeight = 56.dp
    const val DEPART_MILLIS = 480
}
