package dev.huecoder.md3agent.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Pan_tool
import com.composables.icons.materialsymbols.roundedfilled.Auto_awesome
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ChatActivity

@Composable
fun WorkingBadge(activity: ChatActivity, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = MaterialTheme.colorScheme
    val morph = remember { Morph(BadgePolygon, MaterialShapes.SoftBurst) }
    val transition = rememberInfiniteTransition(label = "working")
    val sweep by transition.animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing)), label = "sweep")
    val breath by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(BREATH_MS), RepeatMode.Reverse), label = "breath")
    val needsInput = activity == ChatActivity.NEEDS_INPUT
    val gradient = if (needsInput) {
        listOf(colors.tertiary, colors.tertiaryContainer, colors.tertiary)
    } else {
        listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary)
    }
    val label = stringResource(if (needsInput) R.string.home_chat_needs_input else R.string.home_chat_working)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(MorphShape(morph, progress = if (needsInput) 0f else breath, rotationDegrees = sweep / SHAPE_SPIN_DIVIDER))
            .drawBehind { rotate(sweep) { drawRect(Brush.sweepGradient(gradient, center)) } },
    ) {
        Icon(
            if (needsInput) MaterialSymbols.Rounded.Pan_tool else MaterialSymbols.RoundedFilled.Auto_awesome,
            contentDescription = label,
            tint = if (needsInput) colors.onTertiary else colors.onPrimary,
            modifier = Modifier.size(size * ICON_FRACTION),
        )
    }
}

private const val FULL_TURN = 360f
private const val SWEEP_MS = 2_400
private const val BREATH_MS = 900
private const val SHAPE_SPIN_DIVIDER = 4f
private const val ICON_FRACTION = 0.5f
