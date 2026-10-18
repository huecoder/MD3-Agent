package dev.huecoder.md3agent.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

val LocalPageOffset = compositionLocalOf<() -> Float> { { 0f } }

@Composable
fun Modifier.reveal(order: Int): Modifier {
    val offset = LocalPageOffset.current
    return graphicsLayer { translationX = offset() * order * OnboardingMotion.TRAIL.toPx() }
}
