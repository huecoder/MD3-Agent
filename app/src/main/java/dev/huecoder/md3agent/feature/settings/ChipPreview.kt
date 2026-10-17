package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ChipContent
import dev.huecoder.md3agent.system.notifications.ChipText
import dev.huecoder.md3agent.system.notifications.ChipUnits
import kotlinx.coroutines.delay

@Composable
internal fun ChipPreview(content: ChipContent, modifier: Modifier = Modifier) {
    val units = ChipUnits.of(LocalResources.current)
    val step by produceState(0) {
        while (true) {
            delay(SAMPLE_MS)
            value = (value + 1) % SAMPLES.size
        }
    }
    val sample = SAMPLES[step]
    Surface(color = Color.Black, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(ChipText.working(content, sample.elapsedMs, sample.tokens, units))
            Chip(stringResource(R.string.chip_ask))
            Chip(stringResource(R.string.chip_done))
        }
    }
}

@Composable
private fun Chip(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.clip(CircleShape).background(colors.primaryContainer).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_notification), contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

private data class Sample(val elapsedMs: Long, val tokens: Int)

private val SAMPLES = listOf(
    Sample(elapsedMs = 12_000, tokens = 210),
    Sample(elapsedMs = 45_000, tokens = 1_234),
    Sample(elapsedMs = 130_000, tokens = 6_400),
    Sample(elapsedMs = 14 * 60_000L, tokens = 48_000),
    Sample(elapsedMs = 65 * 60_000L, tokens = 310_000),
)

private const val SAMPLE_MS = 1_600L
