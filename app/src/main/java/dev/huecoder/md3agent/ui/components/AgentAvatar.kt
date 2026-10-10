package dev.huecoder.md3agent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.ui.avatar.AgentLook
import dev.huecoder.md3agent.ui.avatar.BotAvatar
import dev.huecoder.md3agent.ui.avatar.KnockoutStage
import dev.huecoder.md3agent.ui.avatar.LocalAgentLook
import dev.huecoder.md3agent.ui.avatar.TypingAttention

@Composable
fun AgentAvatar(
    isWorking: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    look: AgentLook = LocalAgentLook.current,
    interactive: Boolean = false,
    typing: TypingAttention? = null,
    listeningLevel: Float? = null,
    knockout: KnockoutStage? = null,
    watchingBelow: Boolean = false,
) {
    val colors = look.tint.boldColors()
    BotAvatar(
        isWorking = isWorking,
        restShape = look.shape,
        workingShapes = look.workingShapes,
        size = size,
        modifier = modifier,
        bodyColor = colors.container,
        eyeColor = colors.content,
        punchable = interactive,
        typing = typing,
        listeningLevel = listeningLevel,
        knockout = knockout,
        watchingBelow = watchingBelow,
    )
}
