package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Mic
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.glow.EdgeGlow
import dev.huecoder.md3agent.assistant.glow.GlowEnvelope
import dev.huecoder.md3agent.data.settings.AssistantLayout
import dev.huecoder.md3agent.data.settings.AssistantSettings
import dev.huecoder.md3agent.ui.components.AgentAvatar
import kotlinx.coroutines.delay

@Composable
internal fun InvokePreview(settings: AssistantSettings, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scaled = Density(density.density * PREVIEW_SCALE, density.fontScale)
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(round, settings.glow, settings.glowGrid) {
        delay(((GlowEnvelope().durationSeconds + REPLAY_GAP_SECONDS) * MILLIS_PER_SECOND).toLong())
        round++
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalDensity provides scaled) {
            Box(
                Modifier
                    .size(PHONE_WIDTH, PHONE_HEIGHT)
                    .clip(RoundedCornerShape(PHONE_CORNER))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { round++ },
            ) {
                Wallpaper()
                when (settings.layout) {
                    AssistantLayout.PANEL -> PanelMock()
                    AssistantLayout.COMPACT -> CompactMock(showOrb = settings.showOrb)
                }
                key(round, settings.glow, settings.glowGrid) {
                    EdgeGlow(settings.glow, settings.glowGrid, play = true, cornerRadius = PHONE_CORNER)
                }
            }
        }
    }
}

@Composable
private fun Wallpaper() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerLowest)))) {
        Column(Modifier.padding(top = 56.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(ICON_ROWS) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    repeat(ICON_COLUMNS) { Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(colors.onPrimaryContainer.copy(alpha = ICON_ALPHA))) }
                }
            }
        }
    }
}

@Composable
private fun PanelMock() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = PANEL_SCRIM_ALPHA)))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(PANEL_HEIGHT)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surfaceContainer),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(vertical = 10.dp).size(width = 32.dp, height = 4.dp).clip(CircleShape).background(colors.onSurfaceVariant.copy(alpha = HANDLE_ALPHA)))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AgentAvatar(isWorking = false, size = 56.dp)
                    Text(stringResource(R.string.chat_input_placeholder), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                }
            }
            MockComposer(Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun CompactMock(showOrb: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            if (showOrb) AgentAvatar(isWorking = false, size = 56.dp)
            MockComposer(Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun MockComposer(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(colors.surfaceContainerHigh).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(MaterialSymbols.Rounded.Add, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).size(24.dp))
        Text(stringResource(R.string.chat_input_placeholder), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbols.Rounded.Mic, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

private const val PREVIEW_SCALE = 0.42f
private const val REPLAY_GAP_SECONDS = 1.2f
private const val MILLIS_PER_SECOND = 1_000f
private const val ICON_ROWS = 3
private const val ICON_COLUMNS = 4
private const val ICON_ALPHA = 0.14f
private const val PANEL_SCRIM_ALPHA = 0.18f
private const val HANDLE_ALPHA = 0.4f
private val PHONE_WIDTH = 360.dp
private val PHONE_HEIGHT = 720.dp
private val PHONE_CORNER = 40.dp
private val PANEL_HEIGHT = 300.dp
