package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.ModelChipHeight
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString

@Composable
fun ContextPill(meter: ContextMeter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = meter.description()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(min = ModelChipHeight).padding(start = 10.dp, end = 14.dp),
        ) {
            ContextRing(meter.fraction, RING_SIZE)
            Text(meter.tokenLabel(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun ContextRingButton(meter: ContextMeter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = meter.description()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.size(RING_BUTTON_SIZE).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { ContextRing(meter.fraction, RING_BUTTON_RING) }
    }
}

@Composable
fun ContextRing(fraction: Float, size: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    PieChart(listOf(fraction.toDouble(), 1.0 - fraction), listOf(colors.primary, colors.surfaceContainerHighest), modifier.size(size))
}

@Composable
fun ContextMeter.tokenLabel(): String = UiText.Resource(R.string.chat_context_label, tokenTexts()).asString()

@Composable
fun ContextMeter.description(): String = UiText.Resource(R.string.chat_context_description, tokenTexts()).asString()

private fun ContextMeter.tokenTexts() = listOf(ReplyMetaFormat.tokens(used), ReplyMetaFormat.tokens(limit))

private val RING_SIZE = 20.dp
private val RING_BUTTON_SIZE = 40.dp
private val RING_BUTTON_RING = 22.dp
