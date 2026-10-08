package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.composables.icons.materialsymbols.rounded.Language
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.composables.icons.materialsymbols.rounded.Send
import dev.huecoder.md3agent.tools.ButtonAction
import dev.huecoder.md3agent.tools.ReplyButton
import dev.huecoder.md3agent.ui.components.copyText
import kotlinx.coroutines.launch

@Composable
fun SourcePills(sources: List<SourceUi>, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        sources.forEach { source ->
            AssistChip(
                onClick = { uriHandler.openUri(source.url) },
                label = { Text(source.host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Favicon(source.host) },
                shape = CircleShape,
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = null,
            )
        }
    }
}

@Composable
private fun Favicon(host: String) {
    val fallback = rememberVectorPainter(MaterialSymbols.Rounded.Language)
    AsyncImage(
        model = "https://$host/favicon.ico",
        contentDescription = null,
        placeholder = fallback,
        error = fallback,
        fallback = fallback,
        modifier = Modifier.size(AssistChipDefaults.IconSize).clip(CircleShape),
    )
}

@Composable
fun SuggestionChips(suggestions: List<String>, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        suggestions.forEach { suggestion ->
            SuggestionChip(onClick = { onSend(suggestion) }, label = { Text(suggestion) }, shape = CircleShape)
        }
    }
}

@Composable
fun ReplyButtons(buttons: List<ReplyButton>, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        buttons.forEach { button ->
            FilledTonalButton(
                onClick = {
                    when (button.action) {
                        ButtonAction.OPEN_URL -> uriHandler.openUri(button.value)
                        ButtonAction.COPY -> scope.launch { clipboard.copyText(button.value) }
                        ButtonAction.SEND -> onSend(button.value)
                    }
                },
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                val icon = when (button.action) {
                    ButtonAction.OPEN_URL -> MaterialSymbols.Rounded.Open_in_new
                    ButtonAction.COPY -> MaterialSymbols.Rounded.Content_copy
                    ButtonAction.SEND -> MaterialSymbols.Rounded.Send
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(button.label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}
