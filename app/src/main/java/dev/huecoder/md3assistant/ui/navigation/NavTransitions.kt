package dev.huecoder.md3assistant.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

internal object NavTransitions {
    private const val DURATION_MS = 400
    private const val PARALLAX_DIVISOR = 10
    private const val PREDICTIVE_BACK_SCALE = 0.9f
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    private fun <T> motion() = tween<T>(DURATION_MS, easing = Emphasized)

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(motion()) { it / PARALLAX_DIVISOR } + fadeIn(motion())
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(motion()) { -it / PARALLAX_DIVISOR } + fadeOut(motion())
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(motion()) { -it / PARALLAX_DIVISOR } + fadeIn(motion())
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        scaleOut(motion(), targetScale = PREDICTIVE_BACK_SCALE) + fadeOut(motion())
    }
}
