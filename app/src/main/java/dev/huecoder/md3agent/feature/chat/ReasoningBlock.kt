package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Neurology
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.ExpandableBlock

@Composable
fun ReasoningBlock(reasoning: String, isStreaming: Boolean) {
    ExpandableBlock(
        title = stringResource(if (isStreaming) R.string.chat_reasoning_streaming else R.string.chat_reasoning),
        leading = {
            Icon(
                MaterialSymbols.Rounded.Neurology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(StepIconSize),
            )
        },
    ) {
        Text(
            reasoning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
