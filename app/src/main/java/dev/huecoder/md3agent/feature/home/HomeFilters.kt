package dev.huecoder.md3agent.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Archive
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Folder
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.db.FolderEntity

@Composable
fun HomeFilters(
    onOpenChat: (String) -> Unit,
    onOpenMessage: (chatId: String, messageId: String) -> Unit,
    folders: List<FolderEntity>,
    filter: ChatFilter,
    showArchive: Boolean,
    showTrash: Boolean,
    onFilter: (ChatFilter) -> Unit,
    onFolderMenu: (FolderEntity) -> Unit,
    onNewFolder: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        ChatSearch(onOpenChat, onOpenMessage)
        FilterChips(folders, filter, showArchive, showTrash, onFilter, onFolderMenu, onNewFolder)
    }
}

@Composable
private fun FilterChips(
    folders: List<FolderEntity>,
    selected: ChatFilter,
    showArchive: Boolean,
    showTrash: Boolean,
    onSelect: (ChatFilter) -> Unit,
    onMenu: (FolderEntity) -> Unit,
    onNew: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        FolderChip(stringResource(R.string.home_folder_all), icon = null, selected = selected == ChatFilter.All, onClick = { onSelect(ChatFilter.All) })
        folders.forEach { folder ->
            val filter = ChatFilter.Folder(folder.id)
            FolderChip(
                label = folder.name,
                icon = MaterialSymbols.Rounded.Folder,
                selected = selected == filter,
                onClick = { onSelect(filter) },
                onLongClick = { onMenu(folder) },
            )
        }
        if (showArchive || selected == ChatFilter.Archive) {
            FolderChip(stringResource(R.string.home_archive), MaterialSymbols.Rounded.Archive, selected == ChatFilter.Archive, onClick = { onSelect(ChatFilter.Archive) })
        }
        if (showTrash || selected == ChatFilter.Trash) {
            FolderChip(stringResource(R.string.home_trash), MaterialSymbols.Rounded.Delete, selected == ChatFilter.Trash, onClick = { onSelect(ChatFilter.Trash) })
        }
        FolderChip(stringResource(R.string.home_folder_new), icon = MaterialSymbols.Rounded.Add, selected = false, onClick = onNew)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent, label = "chipContainer")
    val content = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClick?.let { stringResource(R.string.home_folder_rename) }),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(min = CHIP_HEIGHT).padding(horizontal = 16.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private val CHIP_HEIGHT = 32.dp
