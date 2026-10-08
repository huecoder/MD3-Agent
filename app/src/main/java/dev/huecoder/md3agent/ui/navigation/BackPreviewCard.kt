package dev.huecoder.md3agent.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import kotlinx.coroutines.flow.first

@Composable
fun AnimatedVisibilityScope.BackPreviewCard(entry: NavBackStackEntry, navController: NavController, content: @Composable () -> Unit) {
    val progress = transition.animateFloat(
        transitionSpec = { tween(NavTransitions.DURATION_MS, easing = LinearEasing) },
        label = "backProgress",
    ) { if (it == EnterExitState.PostExit) 1f else 0f }
    var releasedAt by remember(entry) { mutableStateOf<Float?>(null) }

    LaunchedEffect(entry) {
        navController.currentBackStack.first { entry !in it }
        releasedAt = progress.value
    }

    val backStack by navController.currentBackStack.collectAsState()
    val isPopped by rememberUpdatedState(
        transition.targetState == EnterExitState.PostExit && (entry !in backStack || backStack.lastOrNull() == entry),
    )
    val surface = MaterialTheme.colorScheme.surface

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { if (isPopped) drawRect(Color.Black.copy(alpha = SCRIM_ALPHA * (1f - progress.value))) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!isPopped) return@graphicsLayer
                    val p = progress.value
                    val scale = lerp(1f, MIN_SCALE, p)
                    scaleX = scale
                    scaleY = scale
                    alpha = fadeAfterRelease(p, releasedAt)
                    shape = RoundedCornerShape(lerp(0f, CORNER_RADIUS.toPx(), (p / CORNER_RAMP).coerceAtMost(1f)))
                    clip = true
                }
                .background(surface),
        ) { content() }
    }
}

private fun fadeAfterRelease(progress: Float, releasedAt: Float?): Float {
    val start = releasedAt ?: return 1f
    if (start >= 1f) return 0f
    val fraction = ((progress - start) / (1f - start)).coerceIn(0f, 1f)
    return 1f - FastOutSlowInEasing.transform(fraction)
}

private const val MIN_SCALE = 0.86f
private const val SCRIM_ALPHA = 0.32f
private const val CORNER_RAMP = 0.08f
private val CORNER_RADIUS = 32.dp
