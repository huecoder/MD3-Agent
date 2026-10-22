package dev.huecoder.md3agent.ui.navigation

import android.graphics.Path
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.animation.PathInterpolator
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.max

@Composable
internal fun rememberBackMotion(navController: NavController): BackMotion {
    val owner = checkNotNull(LocalNavigationEventDispatcherOwner.current) { "No NavigationEventDispatcherOwner in the composition" }
    val dispatcher = owner.navigationEventDispatcher
    val density by rememberUpdatedState(LocalDensity.current)
    val view = LocalView.current
    val motion = remember(navController) {
        BackMotion(
            pxPerDp = { density.density },
            displayCornerRadius = view::displayCornerRadius,
            easings = BackEasings(gesture = BackGestureEasing, commit = emphasizedEasing()),
        )
    }
    LaunchedEffect(motion, dispatcher, navController) {
        motion.track(dispatcher, navController.navigatorProvider.getNavigator(ComposeNavigator::class.java).backStack)
    }
    return motion
}

@Composable
internal fun AnimatedVisibilityScope.BackScreen(entry: NavBackStackEntry, motion: BackMotion, content: @Composable () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    transition.animateFloat(
        transitionSpec = { tween(BACK_COMMIT_MS, easing = LinearEasing) },
        label = "backHold",
    ) { if (it == EnterExitState.PostExit) 1f else 0f }
    Box(
        Modifier
            .fillMaxSize()
            .backCard(motion, entry.id)
            .background(surface)
            .backScrim(motion, entry.id, scrimPeak(surface)),
    ) { content() }
}

@Composable
internal fun Modifier.backStage(motion: BackMotion): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    val peak = scrimPeak(surface)
    return onSizeChanged { motion.size = it }
        .background(surface)
        .drawBehind {
            val alpha = peak * motion.frame.scrim
            if (alpha > 0f) drawRect(Color.Black.copy(alpha = alpha))
        }
}

private fun Modifier.backCard(motion: BackMotion, id: String): Modifier = graphicsLayer {
    when (motion.roleOf(id)) {
        BackRole.CLOSING -> card(motion.frame.closing, motion.frame.cornerRadius, motion.frame.closingAlpha)
        BackRole.ENTERING -> card(motion.frame.entering, motion.frame.cornerRadius, 1f)
        BackRole.GONE -> alpha = 0f
        BackRole.NONE -> Unit
    }
}

private fun Modifier.backScrim(motion: BackMotion, id: String, peak: Float): Modifier = drawWithContent {
    drawContent()
    if (motion.roleOf(id) == BackRole.ENTERING) drawRect(Color.Black.copy(alpha = peak * motion.frame.scrim))
}

private fun GraphicsLayerScope.card(rect: Rect, cornerRadius: Float, cardAlpha: Float) {
    if (size.width <= 0f) return
    val scale = rect.width / size.width
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = rect.left
    translationY = rect.top
    scaleX = scale
    scaleY = scale
    alpha = cardAlpha
    clip = true
    shape = RoundedCornerShape(cornerRadius / max(scale, MIN_CARD_SCALE))
}

private fun scrimPeak(surface: Color) = if (surface.luminance() < DARK_SURFACE_LUMINANCE) SCRIM_DARK else SCRIM_LIGHT

private fun View.displayCornerRadius(): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0f
    val insets = rootWindowInsets ?: return 0f
    return DISPLAY_CORNERS.maxOf { insets.getRoundedCorner(it)?.radius?.toFloat() ?: 0f }
}

private fun emphasizedEasing(): Easing {
    val path = Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    }
    val interpolator = PathInterpolator(path)
    return Easing { interpolator.getInterpolation(it) }
}

private val DISPLAY_CORNERS = intArrayOf(
    RoundedCorner.POSITION_TOP_LEFT,
    RoundedCorner.POSITION_TOP_RIGHT,
    RoundedCorner.POSITION_BOTTOM_RIGHT,
    RoundedCorner.POSITION_BOTTOM_LEFT,
)
private const val MIN_CARD_SCALE = 0.01f
private const val DARK_SURFACE_LUMINANCE = 0.5f
private const val SCRIM_DARK = 0.8f
private const val SCRIM_LIGHT = 0.2f
