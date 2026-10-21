package dev.huecoder.md3agent.feature.onboarding.sims

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.AgentAvatar
import kotlinx.coroutines.delay

@Composable
fun ScreenScene(modifier: Modifier = Modifier, onCycle: () -> Unit = {}) {
    val reply = stringResource(R.string.onboarding_scene_screen_reply)
    val slide = remember { Animatable(0f) }
    var typed by remember { mutableIntStateOf(0) }

    LaunchedEffect(reply) {
        while (true) {
            typed = 0
            slide.snapTo(0f)
            delay(HOLD_MS)
            slide.animateTo(1f, tween(SLIDE_MS, easing = FastOutSlowInEasing))
            typeInto(reply, CHAR_MS) { typed = it }
            delay(HOLD_MS * 2)
            slide.animateTo(0f, tween(SLIDE_MS, easing = FastOutSlowInEasing))
            onCycle()
        }
    }

    SceneFrame(modifier) {
        MockMail()
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp)
                .graphicsLayer { translationY = (1f - slide.value) * PANEL_TRAVEL.dp.toPx(); alpha = slide.value.coerceAtLeast(0f) }
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgentAvatar(isWorking = typed in 1 until reply.length, size = 30.dp)
            Text(reply.take(typed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun MockMail() {
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
        repeat(4) { index ->
            Box(Modifier.fillMaxWidth(if (index == 3) 0.6f else 1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(line))
        }
        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer))
    }
}

private const val HOLD_MS = 900L
private const val SLIDE_MS = 480
private const val CHAR_MS = 26L
private const val PANEL_TRAVEL = 80f
