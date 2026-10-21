package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.ModelChipHeight
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString

@Composable
fun ContextPill(meter: ContextMeter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = listOf(ReplyMetaFormat.tokens(meter.used), ReplyMetaFormat.tokens(meter.limit))
    val description = UiText.Resource(R.string.chat_context_description, tokens).asString()
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
            CircularProgressIndicator(
                progress = { meter.fraction },
                modifier = Modifier.size(RING_SIZE),
                strokeWidth = RING_STROKE,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text(UiText.Resource(R.string.chat_context_label, tokens).asString(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private val RING_SIZE = 20.dp
private val RING_STROKE = 3.dp
