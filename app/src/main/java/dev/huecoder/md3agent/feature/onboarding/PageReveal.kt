package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

val LocalPageArrival = compositionLocalOf<() -> Float> { { 1f } }

@Composable
fun Modifier.reveal(order: Int): Modifier {
    val arrival = LocalPageArrival.current
    return graphicsLayer {
        val head = order * OnboardingMotion.STAGGER
        val local = ((arrival() - head) / (1f - head)).coerceIn(0f, 1f)
        alpha = local
        translationY = (1f - local) * OnboardingMotion.REVEAL_DISTANCE.toPx()
    }
}

fun arrivalOf(offsetInPages: Float): Float = (1f - abs(offsetInPages)).coerceIn(0f, 1f)
