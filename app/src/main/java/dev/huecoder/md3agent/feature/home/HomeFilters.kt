package dev.huecoder.md3agent.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.ChatTint
import dev.huecoder.md3agent.data.chat.FolderLook
import dev.huecoder.md3agent.data.chat.look
import dev.huecoder.md3agent.data.db.FolderEntity
import dev.huecoder.md3agent.ui.components.boldColors
import dev.huecoder.md3agent.ui.components.colors
import dev.huecoder.md3agent.ui.components.icon

@Composable
fun HomeFilters(
    onOpenChat: (String) -> Unit,
    onOpenMessage: (chatId: String, messageId: String) -> Unit,
    folders: List<FolderEntity>,
    filter: ChatFilter,
    onFilter: (ChatFilter) -> Unit,
    onFolderMenu: (FolderEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        ChatSearch(onOpenChat, onOpenMessage)
        if (folders.isNotEmpty()) FilterChips(folders, filter, onFilter, onFolderMenu)
    }
}

@Composable
private fun FilterChips(folders: List<FolderEntity>, selected: ChatFilter, onSelect: (ChatFilter) -> Unit, onMenu: (FolderEntity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        FolderChip(stringResource(R.string.home_folder_all), look = null, selected = selected == ChatFilter.All, onClick = { onSelect(ChatFilter.All) })
        folders.forEach { folder ->
            val filter = ChatFilter.Folder(folder.id)
            FolderChip(
                label = folder.name,
                look = folder.look,
                selected = selected == filter,
                onClick = { onSelect(filter) },
                onLongClick = { onMenu(folder) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    label: String,
    look: FolderLook?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val tint = look?.tint ?: ChatTint.THEME
    val resting = tint.colors()
    val bold = tint.boldColors()
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) bold.container else colors.surfaceContainerHigh, label = "chipContainer")
    val content by animateColorAsState(if (selected) bold.content else colors.onSurface, label = "chipContent")
    val iconTint by animateColorAsState(if (selected) bold.content else resting.content, label = "chipIcon")
    val corner by animateDpAsState(if (selected) SELECTED_CORNER else CHIP_HEIGHT / 2, label = "chipCorner")
    val shape = RoundedCornerShape(corner)
    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClick?.let { stringResource(R.string.home_folder_edit) },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(min = CHIP_HEIGHT).padding(horizontal = 18.dp),
        ) {
            look?.let { Icon(it.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp)) }
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
        }
    }
}

private val CHIP_HEIGHT = 40.dp
private val SELECTED_CORNER = 12.dp
