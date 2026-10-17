package com.mwagent.feature.files

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Create_new_folder
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Description
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Forum
import com.composables.icons.materialsymbols.rounded.Select_all
import com.composables.icons.materialsymbols.rounded.Share
import com.composables.icons.materialsymbols.rounded.Upload_file
import com.mwagent.R
import com.mwagent.data.attachments.ByteSize
import com.mwagent.data.files.EntryKind
import com.mwagent.data.files.LibraryEntry
import com.mwagent.data.files.LibraryLocation
import com.mwagent.data.files.LibraryRoot
import com.mwagent.feature.chat.lightbox.LightboxHost
import com.mwagent.feature.chat.lightbox.LocalLightbox
import com.mwagent.feature.home.NameDialog
import com.mwagent.feature.home.dragToSelect
import com.mwagent.feature.home.RelativeTime
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.ShapeBadge
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.components.groupedListColors
import com.mwagent.ui.files.openFile
import com.mwagent.ui.files.shareFiles
import com.mwagent.ui.haptics.clickHaptics
import com.mwagent.ui.text.resolve
import java.util.Locale

private sealed interface FolderDialog {
    data object NewFolder : FolderDialog
    data class Rename(val entry: LibraryEntry) : FolderDialog
    data object Delete : FolderDialog
}

@Composable
fun FolderScreen(location: LibraryLocation, onOpen: (LibraryLocation) -> Unit, onBack: () -> Unit) {
    val key = "folder-${location.root}-${location.segments.joinToString("/")}-${location.fromChats}"
    val viewModel = appViewModel(key = key) { FolderViewModel(it.library, location) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.upload(uris)
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }
    BackHandler(enabled = state.isSelecting, onBack = viewModel::clearSelection)

    val title = when {
        state.isSelecting -> pluralStringResource(R.plurals.home_selected, state.selection.size, state.selection.size)
        location.fromChats -> stringResource(R.string.files_from_chats)
        location.segments.isNotEmpty() -> location.segments.last()
        else -> stringResource(location.root.title)
    }
    LightboxHost {
        SettingsScaffold(
            title = title,
            onBack = if (state.isSelecting) viewModel::clearSelection else onBack,
            actions = {
                if (state.isSelecting) {
                    BarAction(MaterialSymbols.Rounded.Select_all, R.string.home_select_all, viewModel::selectAll)
                    if (state.canShare) BarAction(MaterialSymbols.Rounded.Share, R.string.files_share) { context.shareFiles(state.sharedFiles) }
                    if (state.canRename) state.selectedEntries.single().let { only -> BarAction(MaterialSymbols.Rounded.Edit, R.string.files_rename) { dialog = FolderDialog.Rename(only) } }
                    if (state.canDelete) BarAction(MaterialSymbols.Rounded.Delete, R.string.files_delete) { dialog = FolderDialog.Delete }
                }
            },
            floatingActionButton = {
                if (!location.fromChats && !state.isSelecting) {
                    FloatingActionButtonMenu(
                        expanded = menuOpen,
                        button = {
                            ToggleFloatingActionButton(checked = menuOpen, onCheckedChange = { menuOpen = it }) {
                                Icon(if (checkedProgress > HALF) MaterialSymbols.Rounded.Close else MaterialSymbols.Rounded.Add, contentDescription = stringResource(R.string.files_add))
                            }
                        },
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = { menuOpen = false; pickFiles.launch(ALL_FILES) },
                            icon = { Icon(MaterialSymbols.Rounded.Upload_file, contentDescription = null) },
                            text = { Text(stringResource(R.string.files_upload)) },
                        )
                        FloatingActionButtonMenuItem(
                            onClick = { menuOpen = false; dialog = FolderDialog.NewFolder },
                            icon = { Icon(MaterialSymbols.Rounded.Create_new_folder, contentDescription = null) },
                            text = { Text(stringResource(R.string.files_new_folder)) },
                        )
                    }
                }
            },
        ) { padding ->
            EntryList(
                entries = state.entries,
                selection = state.selection,
                selectableKeys = state.selectableKeys,
                isLoaded = state.isLoaded,
                padding = padding,
                onClick = { entry ->
                    when {
                        state.isSelecting -> viewModel.toggle(entry)
                        entry.kind == EntryKind.CHAT_FILES -> onOpen(location.copy(fromChats = true))
                        entry.kind == EntryKind.FOLDER -> onOpen(location.child(entry.name))
                        else -> entry.file?.let(context::openFile)
                    }
                },
                onLongClick = viewModel::toggle,
                onSelectRange = viewModel::selectRange,
                onRangeEnd = viewModel::endRange,
            )
        }
    }

    when (val shown = dialog) {
        null -> Unit
        FolderDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.files_new_folder),
            label = stringResource(R.string.files_name),
            initial = "",
            confirmLabel = stringResource(R.string.home_folder_create),
            onConfirm = { viewModel.createFolder(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is FolderDialog.Rename -> NameDialog(
            title = stringResource(R.string.files_rename),
            label = stringResource(R.string.files_name),
            initial = shown.entry.name,
            confirmLabel = stringResource(R.string.settings_save),
            onConfirm = { viewModel.rename(shown.entry, it); viewModel.clearSelection(); dialog = null },
            onDismiss = { dialog = null },
        )
        FolderDialog.Delete -> AlertDialog(
            modifier = Modifier.clickHaptics(),
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.files_delete_title, state.selection.size)) },
            text = { Text(stringResource(R.string.files_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected(); dialog = null }) {
                    Text(stringResource(R.string.files_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.home_delete_chat_cancel)) } },
        )
    }
}

@Composable
internal fun EntryList(
    entries: List<LibraryEntry>,
    selection: Set<String>,
    selectableKeys: List<String>,
    isLoaded: Boolean,
    padding: PaddingValues,
    onClick: (LibraryEntry) -> Unit,
    onLongClick: (LibraryEntry) -> Unit,
    onSelectRange: (anchor: String, current: String) -> Unit,
    onRangeEnd: () -> Unit,
) {
    val lightbox = LocalLightbox.current
    val images = entries.mapNotNull { it.file?.takeIf { file -> file.isImage } }
    val locale = LocalConfiguration.current.locales[0]
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isEntry by rememberUpdatedState<(Any) -> Boolean> { key -> key in selectableKeys }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dragToSelect(listState, scope, isSelectable = { isEntry(it) }, onRange = onSelectRange, onEnd = onRangeEnd),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = FAB_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        if (isLoaded && entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.files_folder_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp, horizontal = 12.dp),
                )
            }
        }
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            val selected = entry.key in selection
            SegmentedListItem(
                onClick = {
                    val image = entry.file?.takeIf { it.isImage && selection.isEmpty() }
                    if (image != null && lightbox != null) lightbox.show(images, images.indexOf(image)) else onClick(entry)
                },
                onLongClick = { onLongClick(entry) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = entries.size),
                colors = if (selected) ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else groupedListColors(),
                leadingContent = { EntryIcon(entry, selected) },
                supportingContent = entry.details(locale)?.let { { Text(it) } },
                modifier = Modifier.animateItem(),
            ) {
                Text(
                    if (entry.kind == EntryKind.CHAT_FILES) stringResource(R.string.files_from_chats) else entry.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
    }
}

@Composable
private fun EntryIcon(entry: LibraryEntry, selected: Boolean) {
    val image = entry.file?.takeIf { it.isImage }
    when {
        selected -> ShapeBadge(MaterialSymbols.Rounded.Check)
        image != null -> AsyncImage(
            model = image.file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(ICON_SIZE).clip(MaterialTheme.shapes.medium),
        )
        entry.kind == EntryKind.CHAT_FILES -> ShapeBadge(MaterialSymbols.Rounded.Forum)
        entry.kind == EntryKind.FOLDER -> ShapeBadge(MaterialSymbols.Rounded.Folder)
        else -> ShapeBadge(MaterialSymbols.Rounded.Description)
    }
}

private fun LibraryEntry.details(locale: Locale): String? = when (kind) {
    EntryKind.CHAT_FILES -> null
    EntryKind.FOLDER -> RelativeTime.format(modifiedAt, locale)
    EntryKind.FILE -> "${ByteSize.describe(sizeBytes)} · ${RelativeTime.format(modifiedAt, locale)}"
}

val LibraryRoot.title: Int
    get() = when (this) {
        LibraryRoot.MINE -> R.string.files_root_mine
        LibraryRoot.AGENT -> R.string.files_root_agent
    }

private val ALL_FILES = arrayOf("*/*")
private const val HALF = 0.5f
private val ICON_SIZE = 44.dp
private val FAB_CLEARANCE = 120.dp
