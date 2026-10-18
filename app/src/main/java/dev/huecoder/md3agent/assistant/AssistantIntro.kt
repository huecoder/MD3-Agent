package dev.huecoder.md3agent.assistant

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.huecoder.md3agent.feature.chat.rememberGreeting
import dev.huecoder.md3agent.ui.components.AgentAvatar

@Composable
internal fun AssistantIntro(chrome: () -> Float, restsAtBottom: Boolean, modifier: Modifier = Modifier) {
    Layout(
        content = {
            AgentAvatar(
                isWorking = false,
                size = AVATAR_SIZE,
                modifier = Modifier.graphicsLayer {
                    val scale = lerp(RESTING_SCALE, 1f, chrome())
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    alpha = if (restsAtBottom) 1f else chrome()
                },
            )
            Text(
                rememberGreeting(),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = GREETING_MARGIN).graphicsLayer { alpha = chrome() },
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val avatar = measurables[0].measure(loose)
        val greeting = measurables[1].measure(loose)
        val greetingGap = GREETING_GAP.roundToPx()
        val restingGap = RESTING_GAP.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val settled = ((constraints.maxHeight - (avatar.height + greetingGap + greeting.height)) / 2).coerceAtLeast(0)
            val resting = if (restsAtBottom) constraints.maxHeight - avatar.height - restingGap else settled
            val top = lerp(resting, settled, chrome())
            avatar.place((constraints.maxWidth - avatar.width) / 2, top)
            greeting.place((constraints.maxWidth - greeting.width) / 2, top + avatar.height + greetingGap)
        }
    }
}

private val AVATAR_SIZE = 64.dp
private val ORB_SIZE = 56.dp
private val RESTING_SCALE = ORB_SIZE / AVATAR_SIZE
private val RESTING_GAP = 12.dp
private val GREETING_GAP = 20.dp
private val GREETING_MARGIN = 32.dp
