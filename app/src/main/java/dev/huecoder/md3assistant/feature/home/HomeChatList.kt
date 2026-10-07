package dev.huecoder.md3assistant.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chat
import dev.huecoder.md3assistant.data.db.ChatSummary
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.ShapeBadge
import dev.huecoder.md3assistant.ui.components.groupedListColors

private val FabClearance = 96.dp

@Composable
fun ChatList(
    groups: List<ChatGroup>,
    contentPadding: PaddingValues,
    onOpenChat: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + FabClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.age}") { SectionHeader(group.age.label) }
            itemsIndexed(group.chats, key = { _, chat -> chat.id }) { index, chat ->
                ChatRow(
                    chat = chat,
                    index = index,
                    count = group.chats.size,
                    onClick = { onOpenChat(chat.id) },
                    onLongClick = { onDeleteRequest(chat.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "Удалить",
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = groupedListColors(),
        modifier = modifier,
        leadingContent = { ShapeBadge(icon = MaterialSymbols.Rounded.Chat) },
        supportingContent = chat.lastMessage?.let(ChatPreview::of)?.takeIf { it.isNotEmpty() }?.let { preview ->
            {
                Text(
                    preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Text(
                RelativeTime.format(chat.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(chat.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

val ChatAge.label: String
    get() = when (this) {
        ChatAge.TODAY -> "Сегодня"
        ChatAge.YESTERDAY -> "Вчера"
        ChatAge.THIS_WEEK -> "На этой неделе"
        ChatAge.EARLIER -> "Ранее"
    }
