package dev.huecoder.md3agent.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
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
import dev.huecoder.md3agent.data.settings.AvatarStyle
import dev.huecoder.md3agent.ui.avatar.BotAvatar
import dev.huecoder.md3agent.ui.avatar.KnockoutStage
import dev.huecoder.md3agent.ui.avatar.LocalAgentLook
import dev.huecoder.md3agent.ui.avatar.Punch
import dev.huecoder.md3agent.ui.avatar.TypingAttention

@Composable
fun AgentAvatar(
    isWorking: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    interactive: Boolean = false,
    typing: TypingAttention? = null,
    listeningLevel: Float? = null,
    knockout: KnockoutStage? = null,
    onPunch: (Punch) -> Unit = {},
) {
    val look = LocalAgentLook.current
    when (look.style) {
        AvatarStyle.BOT -> BotAvatar(
            isWorking = isWorking,
            restShape = look.restShape,
            size = size,
            modifier = modifier,
            punchable = interactive,
            typing = typing,
            listeningLevel = listeningLevel,
            knockout = knockout,
            onPunch = onPunch,
        )
        AvatarStyle.CLASSIC -> PokableClassicAvatar(isWorking, modifier, size, interactive)
    }
}

@Composable
private fun PokableClassicAvatar(isWorking: Boolean, modifier: Modifier, size: Dp, interactive: Boolean) {
    val haptics = rememberHaptics()
    var pokes by remember { mutableIntStateOf(0) }
    val squish = remember { Animatable(1f) }
    LaunchedEffect(pokes) {
        if (pokes == 0) return@LaunchedEffect
        squish.animateTo(SQUISH_SCALE, tween(SQUISH_MS))
        squish.animateTo(1f, SQUISH_RELEASE)
    }
    val touch = if (interactive) {
        Modifier
            .graphicsLayer { scaleX = squish.value; scaleY = squish.value }
            .clickable(interactionSource = null, indication = null) {
                pokes++
                haptics.tap()
            }
    } else {
        Modifier
    }
    ClassicAvatar(isWorking, modifier.then(touch), size)
}

@Composable
fun ClassicAvatar(isWorking: Boolean, modifier: Modifier = Modifier, size: Dp = 32.dp) {
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

private const val SQUISH_SCALE = 0.88f
private const val SQUISH_MS = 70
private val SQUISH_RELEASE = spring<Float>(dampingRatio = 0.35f, stiffness = 500f)
private const val BREATH_MS = 900
private const val SPIN_MS = 6_000
private const val FULL_TURN = 360f
private const val ICON_FRACTION = 0.55f
