package dev.huecoder.md3assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.Auto_awesome

@Composable
fun AgentAvatar(isWorking: Boolean, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val morph = remember { Morph(BadgePolygon, MaterialShapes.SoftBurst) }
    val transition = rememberInfiniteTransition(label = "avatar")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS), RepeatMode.Reverse),
        label = "breath",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
        label = "spin",
    )
    val shape = if (isWorking) MorphShape(morph, breath, spin) else MorphShape(morph, progress = 0f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
    ) {
        Icon(
            MaterialSymbols.RoundedFilled.Auto_awesome,
            contentDescription = null,
            tint = if (isWorking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * ICON_FRACTION),
        )
    }
}

private const val BREATH_MS = 900
private const val SPIN_MS = 6_000
private const val FULL_TURN = 360f
private const val ICON_FRACTION = 0.55f
