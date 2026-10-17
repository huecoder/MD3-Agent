package dev.huecoder.md3agent.assistant

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Description
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.EntryKind
import dev.huecoder.md3agent.data.files.FileLibrary
import dev.huecoder.md3agent.data.files.LibraryEntry
import dev.huecoder.md3agent.data.files.LibraryLocation
import dev.huecoder.md3agent.data.files.LibraryRoot
import dev.huecoder.md3agent.data.files.ViewableFile
import dev.huecoder.md3agent.feature.files.EntryIcon
import dev.huecoder.md3agent.feature.files.details
import dev.huecoder.md3agent.feature.settings.CHOICE_HEIGHT
import dev.huecoder.md3agent.feature.settings.Choice
import dev.huecoder.md3agent.feature.settings.ChoicePill
import dev.huecoder.md3agent.ui.components.BackButton

internal enum class AppFilesTab(@StringRes val label: Int) {
    AGENT(R.string.files_root_agent),
    CHATS(R.string.files_from_chats),
    MINE(R.string.files_root_mine),
}

@Stable
internal class AppFilesState {
    var tab by mutableStateOf(DEFAULT_TAB)
        private set
    var folder by mutableStateOf(emptyList<String>())
        private set
    val chosen = mutableStateListOf<ViewableFile>()

    fun select(tab: AppFilesTab) {
        this.tab = tab
        folder = emptyList()
    }

    fun enter(name: String) {
        folder = folder + name
    }

    fun goUp(): Boolean {
        if (folder.isEmpty()) return false
        folder = folder.dropLast(1)
        return true
    }

    fun toggle(file: ViewableFile) {
        if (!chosen.remove(file)) chosen.add(file)
    }

    fun reset() {
        tab = DEFAULT_TAB
        folder = emptyList()
        chosen.clear()
    }

    private companion object {
        val DEFAULT_TAB = AppFilesTab.CHATS
    }
}

@Composable
internal fun AppFilesPage(state: AppFilesState, library: FileLibrary, onBack: () -> Unit, onAttach: (List<ViewableFile>) -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(PAGE_HEIGHT_FRACTION)) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(
                state.folder.lastOrNull() ?: stringResource(R.string.assistant_attach_app_files),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        ChoicePill(
            choices = TABS,
            selected = state.tab,
            onSelect = state::select,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            fillWidth = true,
            height = CHOICE_HEIGHT,
        )
        AppFilesContent(state, library, Modifier.weight(1f).fillMaxWidth())
        AnimatedVisibility(
            visible = state.chosen.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Button(
                onClick = { onAttach(state.chosen.toList()) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.assistant_app_files_attach, state.chosen.size))
            }
        }
    }
}

@Composable
private fun AppFilesContent(state: AppFilesState, library: FileLibrary, modifier: Modifier) {
    val entries by produceState<List<LibraryEntry>?>(null, state.tab, state.folder) { value = library.browse(state.tab, state.folder) }
    val loaded = entries
    when {
        loaded == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
        loaded.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.files_folder_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.tab == AppFilesTab.CHATS -> ChatFilesGrid(loaded.mapNotNull { it.file }, state, modifier)
        else -> FolderList(loaded, state, modifier)
    }
}

@Composable
private fun ChatFilesGrid(files: List<ViewableFile>, state: AppFilesState, modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_CELL),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
        verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        modifier = modifier,
    ) {
        items(files, key = { it.file.path }) { file ->
            FileTile(file, selected = file in state.chosen, onToggle = { state.toggle(file) })
        }
    }
}

@Composable
private fun FileTile(file: ViewableFile, selected: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHighest)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        if (file.isImage) {
            AsyncImage(model = file.file, contentDescription = file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            DocumentLabel(file)
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(colors.primary.copy(alpha = SELECTED_TINT)))
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(MARK_SIZE).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MaterialSymbols.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(MARK_ICON_SIZE))
            }
        }
    }
}

@Composable
private fun DocumentLabel(file: ViewableFile) {
    Column(
        Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(MaterialSymbols.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FolderList(entries: List<LibraryEntry>, state: AppFilesState, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = modifier,
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            val file = entry.file
            val selected = file != null && file in state.chosen
            SegmentedListItem(
                onClick = { if (file == null) state.enter(entry.name) else state.toggle(file) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = entries.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                leadingContent = { EntryIcon(entry, selected = false) },
                supportingContent = entry.details(locale)?.let { { Text(it) } },
                trailingContent = {
                    if (file == null) {
                        Icon(MaterialSymbols.Rounded.Chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Checkbox(checked = selected, onCheckedChange = null)
                    }
                },
            ) {
                Text(entry.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
    }
}

private suspend fun FileLibrary.browse(tab: AppFilesTab, folder: List<String>): List<LibraryEntry> = when (tab) {
    AppFilesTab.AGENT -> folderEntries(LibraryRoot.AGENT, folder)
    AppFilesTab.MINE -> folderEntries(LibraryRoot.MINE, folder)
    AppFilesTab.CHATS -> (list(LibraryLocation(LibraryRoot.AGENT, fromChats = true)) + list(LibraryLocation(LibraryRoot.MINE, fromChats = true)))
        .distinctBy(LibraryEntry::key)
        .sortedByDescending(LibraryEntry::modifiedAt)
}

private suspend fun FileLibrary.folderEntries(root: LibraryRoot, folder: List<String>): List<LibraryEntry> =
    list(LibraryLocation(root, folder)).filterNot { it.kind == EntryKind.CHAT_FILES }

private val TABS = AppFilesTab.entries.map { Choice(it, it.label) }

private const val PAGE_HEIGHT_FRACTION = 0.8f
private const val SELECTED_TINT = 0.4f
private val GRID_CELL = 104.dp
private val GRID_GAP = 8.dp
private val MARK_SIZE = 24.dp
private val MARK_ICON_SIZE = 16.dp
