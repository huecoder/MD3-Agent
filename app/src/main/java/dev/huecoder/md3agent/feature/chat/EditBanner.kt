package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Edit
import dev.huecoder.md3agent.R

@Composable
fun EditBanner(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(start = 16.dp)) {
        Icon(MaterialSymbols.Rounded.Edit, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.chat_editing),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.chat_edit_cancel), tint = color, modifier = Modifier.size(20.dp))
        }
    }
}
