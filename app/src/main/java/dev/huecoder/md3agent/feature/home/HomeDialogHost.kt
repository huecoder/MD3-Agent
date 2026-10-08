package dev.huecoder.md3agent.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.huecoder.md3agent.R

sealed interface HomeDialog {
    data class Actions(val chatId: String) : HomeDialog
    data class RenameChat(val chatId: String) : HomeDialog
    data class Move(val chatIds: Set<String>) : HomeDialog
    data class NewFolder(val moving: Set<String> = emptySet()) : HomeDialog
    data class FolderMenu(val folderId: String) : HomeDialog
    data class RenameFolder(val folderId: String) : HomeDialog
    data class Delete(val chatIds: Set<String>) : HomeDialog
}

@Composable
fun HomeDialogHost(dialog: HomeDialog?, state: HomeUiState, viewModel: HomeViewModel, onChange: (HomeDialog?) -> Unit) {
    val dismiss = { onChange(null) }
    val chatTitle = { id: String -> state.groups.firstNotNullOfOrNull { group -> group.chats.firstOrNull { it.id == id } }?.title }
    val folder = { id: String -> state.folders.firstOrNull { it.id == id } }
    when (dialog) {
        null -> Unit
        is HomeDialog.Actions -> ChatActionsSheet(
            title = chatTitle(dialog.chatId).orEmpty(),
            onRename = { onChange(HomeDialog.RenameChat(dialog.chatId)) },
            onMove = { onChange(HomeDialog.Move(setOf(dialog.chatId))) },
            onSelect = { viewModel.toggleSelection(dialog.chatId); dismiss() },
            onDelete = { onChange(HomeDialog.Delete(setOf(dialog.chatId))) },
            onDismiss = dismiss,
        )
        is HomeDialog.RenameChat -> NameDialog(
            title = stringResource(R.string.home_rename_title),
            label = stringResource(R.string.home_chat_name),
            initial = chatTitle(dialog.chatId).orEmpty(),
            confirmLabel = stringResource(R.string.settings_save),
            onConfirm = { viewModel.rename(dialog.chatId, it); dismiss() },
            onDismiss = dismiss,
        )
        is HomeDialog.Move -> MoveToFolderSheet(
            folders = state.folders,
            onMove = { viewModel.move(dialog.chatIds, it); dismiss() },
            onNewFolder = { onChange(HomeDialog.NewFolder(dialog.chatIds)) },
            onDismiss = dismiss,
        )
        is HomeDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.home_folder_new),
            label = stringResource(R.string.home_folder_name),
            initial = "",
            confirmLabel = stringResource(R.string.home_folder_create),
            onConfirm = { viewModel.createFolder(it, dialog.moving); dismiss() },
            onDismiss = dismiss,
        )
        is HomeDialog.FolderMenu -> folder(dialog.folderId)?.let { target ->
            FolderActionsSheet(
                folder = target,
                onRename = { onChange(HomeDialog.RenameFolder(target.id)) },
                onDelete = { viewModel.deleteFolder(target.id); dismiss() },
                onDismiss = dismiss,
            )
        }
        is HomeDialog.RenameFolder -> folder(dialog.folderId)?.let { target ->
            NameDialog(
                title = stringResource(R.string.home_folder_rename),
                label = stringResource(R.string.home_folder_name),
                initial = target.name,
                confirmLabel = stringResource(R.string.settings_save),
                onConfirm = { viewModel.renameFolder(target.id, it); dismiss() },
                onDismiss = dismiss,
            )
        }
        is HomeDialog.Delete -> DeleteChatsDialog(
            count = dialog.chatIds.size,
            onConfirm = { viewModel.delete(dialog.chatIds); dismiss() },
            onDismiss = dismiss,
        )
    }
}
