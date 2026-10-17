package com.mwagent.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.More_vert
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Smart_toy
import com.mwagent.R
import com.mwagent.data.chat.ChatLook
import com.mwagent.feature.home.DeleteChatsDialog
import com.mwagent.feature.home.NameDialog
import com.mwagent.feature.models.label
import com.mwagent.llm.models.ModelFamily
import com.mwagent.ui.components.BackButton
import com.mwagent.ui.components.ChatBadge
import com.mwagent.ui.components.ModelChip
import com.mwagent.ui.haptics.clickHaptics

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
    val modelLabel = state.modelLabel()
    TopAppBar(
        title = {
            if (state.isStarted) {
                StartedChatTitle(title, state.look, onOpenLook = { dialog = ChatDialog.LOOK })
            } else {
                NewChatTitle(title, state.modelFamily, modelLabel, onPickModel)
            }
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
            if (state.isStarted) {
                ChatMenu(
                    modelSummary = listOfNotNull(modelLabel, state.agentName).joinToString(LABEL_SEPARATOR),
                    onPickModel = onPickModel,
                    onRename = { dialog = ChatDialog.RENAME },
                    onLook = { dialog = ChatDialog.LOOK },
                    onDelete = { dialog = ChatDialog.DELETE },
                )
            }
        },
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
private fun StartedChatTitle(title: String, look: ChatLook, onOpenLook: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChatBadge(
            look,
            size = BADGE_SIZE,
            modifier = Modifier.clip(CircleShape).clickable(onClickLabel = stringResource(R.string.chat_look_title), onClick = onOpenLook),
        )
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NewChatTitle(title: String, family: ModelFamily?, modelLabel: String?, onPickModel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.Center) {
            ModelChip(family = family, label = modelLabel, onClick = onPickModel)
        }
    }
}

@Composable
private fun ChatUiState.modelLabel(): String? = modelName?.let { name ->
    listOfNotNull(name, selection?.reasoningEffort?.let { stringResource(it.label) }).joinToString(LABEL_SEPARATOR)
}

@Composable
private fun ChatMenu(modelSummary: String, onPickModel: () -> Unit, onRename: () -> Unit, onLook: () -> Unit, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.More_vert, contentDescription = stringResource(R.string.chat_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.clickHaptics()) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.chat_model_and_agent))
                        Text(
                            modelSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Smart_toy, contentDescription = null) },
                onClick = { open = false; onPickModel() },
            )
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

private const val LABEL_SEPARATOR = " · "
private val BADGE_SIZE = 40.dp
