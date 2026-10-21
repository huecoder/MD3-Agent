package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chevron_left
import com.composables.icons.materialsymbols.rounded.Chevron_right
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.MessageVersions

@Composable
fun VersionSwitcher(versions: MessageVersions, onSelect: ((String) -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ActionIcon(
            MaterialSymbols.Rounded.Chevron_left,
            stringResource(R.string.chat_version_previous),
            enabled = onSelect != null && versions.previousId != null,
        ) { versions.previousId?.let { onSelect?.invoke(it) } }
        Text(
            stringResource(R.string.chat_version_position, versions.position, versions.count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionIcon(
            MaterialSymbols.Rounded.Chevron_right,
            stringResource(R.string.chat_version_next),
            enabled = onSelect != null && versions.nextId != null,
        ) { versions.nextId?.let { onSelect?.invoke(it) } }
    }
}
