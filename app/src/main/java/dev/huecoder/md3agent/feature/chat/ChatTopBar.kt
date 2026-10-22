package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.data.settings.ChatHeaderStyle
import dev.huecoder.md3agent.data.settings.ContextDisplay
import dev.huecoder.md3agent.feature.home.DeleteChatsDialog
import dev.huecoder.md3agent.feature.home.NameDialog
import dev.huecoder.md3agent.feature.models.label
import dev.huecoder.md3agent.ui.components.BackButton
import dev.huecoder.md3agent.ui.components.ChatBadge
import dev.huecoder.md3agent.ui.components.ModelChip
import dev.huecoder.md3agent.ui.haptics.clickHaptics

private enum class ChatDialog { RENAME, DELETE, LOOK }

class ChatMenuActions(
    val pickModel: () -> Unit,
    val openContext: () -> Unit,
    val rename: () -> Unit,
    val look: () -> Unit,
    val delete: () -> Unit,
) {
    companion object {
        val None = ChatMenuActions({}, {}, {}, {}, {})
    }
}

@Composable
fun ChatTopBar(
    title: String,
    state: ChatUiState,
    onBack: () -> Unit,
    onPickModel: () -> Unit,
    onOpenContext: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onLookChange: (ChatLook) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<ChatDialog?>(null) }
    val actions = ChatMenuActions(
        pickModel = onPickModel,
        openContext = onOpenContext,
        rename = { dialog = ChatDialog.RENAME },
        look = { dialog = ChatDialog.LOOK },
        delete = { dialog = ChatDialog.DELETE },
    )
    when (state.header) {
        ChatHeaderStyle.COMPACT -> CompactChatHeader(
            title = title,
            state = state,
            topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            onBack = onBack,
            actions = actions,
            modifier = modifier,
        )
        ChatHeaderStyle.CLASSIC -> ClassicChatBar(title, state, onBack, actions, modifier)
    }
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
internal fun ClassicChatBar(
    title: String,
    state: ChatUiState,
    onBack: () -> Unit,
    actions: ChatMenuActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        title = {
            if (state.isStarted) {
                StartedChatTitle(title, state.look, onOpenLook = actions.look, onRename = actions.rename)
            } else {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
            if (state.isStarted) {
                state.context?.let { meter ->
                    when (state.contextDisplay) {
                        ContextDisplay.NUMBERS -> ContextPill(meter, onClick = actions.openContext, modifier = Modifier.padding(end = 4.dp))
                        ContextDisplay.RING -> ContextRingButton(meter, onClick = actions.openContext, modifier = Modifier.padding(end = 4.dp))
                        ContextDisplay.MENU -> Unit
                    }
                }
                ChatMenu(state, actions)
            } else {
                ModelChip(family = state.modelFamily, label = state.modelLabel(), onClick = actions.pickModel, modifier = Modifier.padding(end = 8.dp))
            }
        },
        windowInsets = windowInsets,
        modifier = modifier,
    )
}

@Composable
private fun StartedChatTitle(title: String, look: ChatLook, onOpenLook: () -> Unit, onRename: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChatBadge(
            look,
            size = BADGE_SIZE,
            modifier = Modifier.clip(CircleShape).clickable(onClickLabel = stringResource(R.string.chat_look_title), onClick = onOpenLook),
        )
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.renameOnLongPress(onRename))
    }
}

@Composable
private fun ChatUiState.modelLabel(): String? = modelName?.let { name ->
    listOfNotNull(name, selection?.reasoningEffort?.let { stringResource(it.label) }).joinToString(LABEL_SEPARATOR)
}

@Composable
private fun ChatMenu(state: ChatUiState, actions: ChatMenuActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.More_vert, contentDescription = stringResource(R.string.chat_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.clickHaptics()) {
            ChatMenuItems(state, actions) { open = false }
        }
    }
}

@Composable
internal fun ColumnScope.ChatMenuItems(state: ChatUiState, actions: ChatMenuActions, dismiss: () -> Unit) {
    val meter = state.context.takeIf { state.contextDisplay == ContextDisplay.MENU }
    val modelSummary = listOfNotNull(state.modelLabel(), state.agentName).joinToString(LABEL_SEPARATOR)
    if (meter != null) {
        DropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(R.string.chat_context_title))
                    Text(meter.tokenLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            },
            leadingIcon = { ContextRing(meter.fraction, MENU_RING_SIZE) },
            onClick = { dismiss(); actions.openContext() },
        )
    }
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
        onClick = { dismiss(); actions.pickModel() },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.home_rename)) },
        leadingIcon = { Icon(MaterialSymbols.Rounded.Edit, contentDescription = null) },
        onClick = { dismiss(); actions.rename() },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.chat_look_title)) },
        leadingIcon = { Icon(MaterialSymbols.Rounded.Palette, contentDescription = null) },
        onClick = { dismiss(); actions.look() },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.home_delete_chat), color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(MaterialSymbols.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        onClick = { dismiss(); actions.delete() },
    )
}

private const val LABEL_SEPARATOR = " · "
private val BADGE_SIZE = 40.dp
private val MENU_RING_SIZE = 24.dp
