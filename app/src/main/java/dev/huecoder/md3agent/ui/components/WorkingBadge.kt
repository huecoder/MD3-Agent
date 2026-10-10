package dev.huecoder.md3agent.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Pan_tool
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ChatActivity
import dev.huecoder.md3agent.ui.avatar.AgentLook
import dev.huecoder.md3agent.ui.avatar.LocalAgentLook

@Composable
fun WorkingBadge(activity: ChatActivity, modifier: Modifier = Modifier, size: Dp = 44.dp, look: AgentLook = LocalAgentLook.current) {
    when (activity) {
        ChatActivity.WORKING -> {
            val label = stringResource(R.string.home_chat_working)
            AgentAvatar(isWorking = true, size = size, look = look, modifier = modifier.semantics { contentDescription = label })
        }
        ChatActivity.NEEDS_INPUT -> NeedsInputBadge(modifier, size)
    }
}

@Composable
private fun NeedsInputBadge(modifier: Modifier, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "needs-input")
    val sweep by transition.animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing)), label = "sweep")
    val gradient = listOf(colors.tertiary, colors.tertiaryContainer, colors.tertiary)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(BadgePolygon.toShape(startAngle = (sweep / SHAPE_SPIN_DIVIDER).toInt()))
            .drawBehind { rotate(sweep) { drawRect(Brush.sweepGradient(gradient, center)) } },
    ) {
        Icon(
            MaterialSymbols.Rounded.Pan_tool,
            contentDescription = stringResource(R.string.home_chat_needs_input),
            tint = colors.onTertiary,
            modifier = Modifier.size(size * ICON_FRACTION),
        )
    }
}

private const val FULL_TURN = 360f
private const val SWEEP_MS = 2_400
private const val SHAPE_SPIN_DIVIDER = 4f
private const val ICON_FRACTION = 0.5f
