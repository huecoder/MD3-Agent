package com.mwagent.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mwagent.R

sealed interface HomeDialog {
    data class Trashed(val chatId: String) : HomeDialog
    data class RenameChat(val chatId: String) : HomeDialog
    data class Move(val chatIds: Set<String>) : HomeDialog
    data class NewFolder(val moving: Set<String> = emptySet()) : HomeDialog
    data class FolderMenu(val folderId: String) : HomeDialog
    data class RenameFolder(val folderId: String) : HomeDialog
    data class DeleteForever(val chatIds: Set<String>) : HomeDialog
    data object EmptyTrash : HomeDialog
}

@Composable
fun HomeDialogHost(dialog: HomeDialog?, state: HomeUiState, viewModel: HomeViewModel, onChange: (HomeDialog?) -> Unit) {
    val dismiss = { onChange(null) }
    val chatTitle = { id: String -> state.groups.firstNotNullOfOrNull { group -> group.chats.firstOrNull { it.id == id } }?.title }
    val folder = { id: String -> state.folders.firstOrNull { it.id == id } }
    when (dialog) {
        null -> Unit
        is HomeDialog.Trashed -> TrashedChatSheet(
            title = chatTitle(dialog.chatId).orEmpty(),
            onSelect = { viewModel.toggleSelection(dialog.chatId); dismiss() },
            onAction = { action -> onChange(viewModel.perform(action, setOf(dialog.chatId))) },
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
        is HomeDialog.DeleteForever -> DeleteChatsDialog(
            count = dialog.chatIds.size,
            onConfirm = { viewModel.deleteForever(dialog.chatIds); dismiss() },
            onDismiss = dismiss,
        )
        HomeDialog.EmptyTrash -> DeleteChatsDialog(
            count = state.visibleIds.size,
            onConfirm = { viewModel.emptyTrash(); dismiss() },
            onDismiss = dismiss,
        )
    }
}

fun HomeViewModel.perform(action: RowAction, chatIds: Set<String>): HomeDialog? {
    when (action) {
        RowAction.MOVE -> return HomeDialog.Move(chatIds)
        RowAction.DELETE_FOREVER -> return HomeDialog.DeleteForever(chatIds)
        RowAction.TRASH -> trash(chatIds)
        RowAction.ARCHIVE -> archive(chatIds)
        RowAction.UNARCHIVE -> unarchive(chatIds)
        RowAction.RESTORE -> restore(chatIds)
    }
    return null
}
