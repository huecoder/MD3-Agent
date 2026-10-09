package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_drop_down
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.More_vert
import com.composables.icons.materialsymbols.rounded.Palette
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.feature.home.DeleteChatsDialog
import dev.huecoder.md3agent.feature.home.NameDialog
import dev.huecoder.md3agent.feature.models.label
import dev.huecoder.md3agent.ui.components.BackButton
import dev.huecoder.md3agent.ui.components.ChatBadge
import dev.huecoder.md3agent.ui.components.VendorGlyph
import dev.huecoder.md3agent.ui.haptics.clickHaptics

private enum class ChatDialog { RENAME, DELETE, LOOK }

@Composable
fun ChatTopBar(
    title: String,
    state: ChatUiState,
    onBack: () -> Unit,
    onPickModel: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onLookChange: (ChatLook) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<ChatDialog?>(null) }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.chatId != null) {
                    ChatBadge(
                        state.look,
                        size = BADGE_SIZE,
                        modifier = Modifier.clip(CircleShape).clickable(onClickLabel = stringResource(R.string.chat_look_title)) { dialog = ChatDialog.LOOK },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ModelChip(state, onPickModel)
                }
            }
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
            if (state.chatId != null) {
                ChatMenu(onRename = { dialog = ChatDialog.RENAME }, onLook = { dialog = ChatDialog.LOOK }, onDelete = { dialog = ChatDialog.DELETE })
            }
        },
        expandedHeight = TOP_BAR_HEIGHT,
    )
    when (dialog) {
        ChatDialog.RENAME -> NameDialog(
            title = stringResource(R.string.home_rename_title),
            label = stringResource(R.string.home_chat_name),
            initial = title,
            confirmLabel = stringResource(R.string.settings_save),
            onConfirm = { onRename(it); dialog = null },
            onDismiss = { dialog = null },
        )
        ChatDialog.LOOK -> ChatLookSheet(state.look, onChange = onLookChange, onDismiss = { dialog = null })
        ChatDialog.DELETE -> DeleteChatsDialog(count = 1, onConfirm = { dialog = null; onDelete() }, onDismiss = { dialog = null })
        null -> Unit
    }
}

@Composable
private fun ChatMenu(onRename: () -> Unit, onLook: () -> Unit, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.More_vert, contentDescription = stringResource(R.string.chat_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.clickHaptics()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_rename)) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Edit, contentDescription = null) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_look_title)) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Palette, contentDescription = null) },
                onClick = { open = false; onLook() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_delete_chat), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun ModelChip(state: ChatUiState, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.heightIn(min = MODEL_CHIP_HEIGHT).padding(start = 8.dp, end = 2.dp),
        ) {
            VendorGlyph(state.modelFamily, size = 12.dp, tint = tint)
            Text(
                listOfNotNull(
                    state.modelName ?: stringResource(R.string.chat_change_model),
                    state.selection?.reasoningEffort?.let { stringResource(it.label) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = MODEL_CHIP_MAX_WIDTH),
            )
            Icon(MaterialSymbols.Rounded.Arrow_drop_down, contentDescription = stringResource(R.string.chat_change_model), tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

private val TOP_BAR_HEIGHT = 72.dp
private val BADGE_SIZE = 40.dp
private val MODEL_CHIP_HEIGHT = 24.dp
private val MODEL_CHIP_MAX_WIDTH = 220.dp
