package dev.huecoder.md3agent.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

@Composable
fun animateReveal(expanded: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (expanded) 1f else 0f,
    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, REVEAL_THRESHOLD),
    label = "reveal",
)

@Composable
fun Collapsible(reveal: State<Float>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val present by remember(reveal) { derivedStateOf { reveal.value > 0f } }
    if (!present) return
    Box(
        modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * reveal.value.coerceIn(0f, 1f)).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer { alpha = reveal.value.coerceIn(0f, 1f) },
    ) { content() }
}

private const val REVEAL_THRESHOLD = 0.001f
