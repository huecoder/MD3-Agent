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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Drive_file_move
import com.composables.icons.materialsymbols.rounded.Select_all
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun HomeScreen(
    onOpenChat: (chatId: String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it.chats, it.settings, it.conversations) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }

    BackHandler(enabled = state.isSelecting, onBack = viewModel::clearSelection)

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
                        BarAction(MaterialSymbols.Rounded.Drive_file_move, R.string.home_move) { dialog = HomeDialog.Move(state.selection) }
                        BarAction(MaterialSymbols.Rounded.Delete, R.string.home_delete_chat) { dialog = HomeDialog.Delete(state.selection) }
                    }
                },
                scrollBehavior = topBarScroll,
            )
        },
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
                    contentPadding = padding,
                    onClick = { id -> if (state.isSelecting) viewModel.toggleSelection(id) else onOpenChat(id) },
                    onLongClick = { id ->
                        if (state.isSelecting) viewModel.toggleSelection(id) else dialog = HomeDialog.Actions(id)
                    },
                    header = {
                        HomeFilters(
                            query = state.query,
                            onQueryChange = viewModel::setQuery,
                            folders = state.folders,
                            selectedFolder = state.folderFilter,
                            onSelectFolder = viewModel::setFolderFilter,
                            onFolderMenu = { dialog = HomeDialog.FolderMenu(it.id) },
                            onNewFolder = { dialog = HomeDialog.NewFolder() },
                        )
                    },
                )
            }
        }
    }

    HomeDialogHost(dialog, state, viewModel, onChange = { dialog = it })
}

@Composable
private fun BarAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(icon, contentDescription = stringResource(label))
    }
}
