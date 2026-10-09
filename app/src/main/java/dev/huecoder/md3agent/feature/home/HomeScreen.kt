package dev.huecoder.md3agent.feature.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.composables.icons.materialsymbols.rounded.Edit
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Select_all
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.models.ModelPickerSheet
import dev.huecoder.md3agent.ui.components.ModelChip
import dev.huecoder.md3agent.ui.components.ModelChipSize
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun HomeScreen(
    onOpenChat: (chatId: String?) -> Unit,
    onOpenMessage: (chatId: String, messageId: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it.chats, it.settings, it.modelCatalog, it.conversations) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val defaultModel by viewModel.defaultModel.collectAsStateWithLifecycle()
    var pickingModel by remember { mutableStateOf(false) }
    val topBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    BackHandler(enabled = state.isSelecting, onBack = viewModel::clearSelection)
    LaunchedEffect(viewModel) {
        viewModel.notices.collectLatest { notice ->
            val count = notice.chatIds.size
            val message = when (notice) {
                is HomeNotice.Trashed -> resources.getQuantityString(R.plurals.home_notice_trashed, count, count)
                is HomeNotice.Archived -> resources.getQuantityString(R.plurals.home_notice_archived, count, count)
            }
            val result = snackbar.showSnackbar(message, actionLabel = resources.getString(R.string.home_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(notice)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    val title = if (state.isSelecting) {
                        pluralStringResource(R.plurals.home_selected, state.selection.size, state.selection.size)
                    } else {
                        stringResource(R.string.home_title)
                    }
                    Text(title)
                },
                navigationIcon = {
                    if (state.isSelecting) {
                        BarAction(MaterialSymbols.Rounded.Close, R.string.home_selection_close, viewModel::clearSelection)
                    }
                },
                actions = {
                    if (state.isSelecting) {
                        BarAction(MaterialSymbols.Rounded.Select_all, R.string.home_select_all, viewModel::selectAll)
                        state.selection.singleOrNull()?.takeIf { state.filter != ChatFilter.Trash }?.let { only ->
                            BarAction(MaterialSymbols.Rounded.Edit, R.string.home_rename) { dialog = HomeDialog.RenameChat(only) }
                        }
                        selectionActions(state.filter).forEach { action ->
                            BarAction(action.icon, action.label) { dialog = viewModel.perform(action, state.selection) }
                        }
                    } else if (state.filter == ChatFilter.Trash) {
                        TextButton(onClick = { dialog = HomeDialog.EmptyTrash }, enabled = state.visibleIds.isNotEmpty(), modifier = Modifier.padding(end = 8.dp)) {
                            Text(stringResource(R.string.home_empty_trash))
                        }
                    } else if (state.hasProvider) {
                        ModelChip(
                            family = defaultModel?.family,
                            label = defaultModel?.name,
                            onClick = { pickingModel = true },
                            size = ModelChipSize.Regular,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
                scrollBehavior = topBarScroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = SNACKBAR_LIFT)) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                !state.isLoaded -> Unit
                !state.hasProvider -> SetupPrompt(onOpenSettings, Modifier.padding(padding))
                !state.hasChats -> EmptyChats(Modifier.padding(padding))
                else -> ChatList(
                    groups = state.groups,
                    activity = state.activity,
                    selection = state.selection,
                    swipes = RowSwipes(
                        right = RowAction.resolve(state.swipeRight, state.filter, isRight = true),
                        left = RowAction.resolve(state.swipeLeft, state.filter, isRight = false),
                    ),
                    emptyText = state.filter.emptyText,
                    contentPadding = padding,
                    onClick = { id ->
                        when {
                            state.isSelecting -> viewModel.toggleSelection(id)
                            state.filter == ChatFilter.Trash -> dialog = HomeDialog.Trashed(id)
                            else -> onOpenChat(id)
                        }
                    },
                    onLongClick = viewModel::toggleSelection,
                    onAction = { action, id -> dialog = viewModel.perform(action, setOf(id)) },
                    onSelectRange = viewModel::selectRange,
                    onRangeEnd = viewModel::endRange,
                    header = {
                        HomeFilters(
                            onOpenChat = onOpenChat,
                            onOpenMessage = onOpenMessage,
                            folders = state.folders,
                            filter = state.filter,
                            showArchive = state.hasArchive,
                            showTrash = state.hasTrash,
                            onFilter = viewModel::setFilter,
                            onFolderMenu = { dialog = HomeDialog.FolderMenu(it.id) },
                            onNewFolder = { dialog = HomeDialog.NewFolder() },
                        )
                    },
                )
            }
        }
    }

    HomeDialogHost(dialog, state, viewModel, onChange = { dialog = it })
    if (pickingModel) {
        ModelPickerSheet(current = defaultModel?.selection, onSelect = viewModel::selectDefaultModel, onDismiss = { pickingModel = false })
    }
}

private fun selectionActions(filter: ChatFilter): List<RowAction> = when (filter) {
    ChatFilter.Trash -> listOf(RowAction.RESTORE, RowAction.DELETE_FOREVER)
    ChatFilter.Archive -> listOf(RowAction.MOVE, RowAction.UNARCHIVE, RowAction.TRASH)
    else -> listOf(RowAction.MOVE, RowAction.ARCHIVE, RowAction.TRASH)
}

@get:StringRes
private val ChatFilter.emptyText: Int
    get() = when (this) {
        ChatFilter.Archive -> R.string.home_archive_empty
        ChatFilter.Trash -> R.string.home_trash_empty
        else -> R.string.home_group_empty
    }

private val SNACKBAR_LIFT = 88.dp

@Composable
private fun BarAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(icon, contentDescription = stringResource(label))
    }
}
