package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import kotlin.math.abs

@Composable
fun DeliberateTouch(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val scaled = remember(base) { SlopScaled(base, OnboardingMotion.SLOP_SCALE) }
    CompositionLocalProvider(LocalViewConfiguration provides scaled, content = content)
}

@Composable
fun rememberDeliberateFling(state: PagerState): TargetedFlingBehavior {
    val snapping = PagerDefaults.flingBehavior(state, snapAnimationSpec = OnboardingMotion.SnapSpring, snapPositionalThreshold = OnboardingMotion.SNAP_THRESHOLD)
    val minimum = with(LocalDensity.current) { OnboardingMotion.FLING_MIN.toPx() }
    return remember(snapping, minimum) { DeliberateFling(snapping, minimum) }
}

private class SlopScaled(private val base: ViewConfiguration, private val scale: Float) : ViewConfiguration by base {
    override val touchSlop: Float get() = base.touchSlop * scale
}

private class DeliberateFling(private val snapping: TargetedFlingBehavior, private val minimumVelocity: Float) : TargetedFlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float, onRemainingDistanceUpdated: (Float) -> Unit): Float {
        val velocity = if (abs(initialVelocity) < minimumVelocity) 0f else initialVelocity
        return with(snapping) { performFling(velocity, onRemainingDistanceUpdated) }
    }
}
