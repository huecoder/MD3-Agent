package dev.huecoder.md3agent.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.ui.components.ChatBadge
import dev.huecoder.md3agent.feature.main.MainNavBarClearance
import dev.huecoder.md3agent.conversation.ChatActivity
import dev.huecoder.md3agent.ui.components.WorkingBadge
import dev.huecoder.md3agent.data.db.ChatSummary
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.groupedListColors

private val FirstSectionGap = 8.dp
private val SectionGap = 24.dp

@Composable
fun ChatList(
    groups: List<ChatGroup>,
    activity: Map<String, ChatActivity>,
    selection: Set<String>,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
    header: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + MainNavBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "filters") { header() }
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.home_search_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        groups.forEachIndexed { groupIndex, group ->
            item(key = "header-${group.age}") {
                SectionHeader(stringResource(group.age.label), topPadding = if (groupIndex == 0) FirstSectionGap else SectionGap)
            }
            itemsIndexed(group.chats, key = { _, chat -> chat.id }) { index, chat ->
                ChatRow(
                    chat = chat,
                    activity = activity[chat.id],
                    selected = chat.id in selection,
                    index = index,
                    count = group.chats.size,
                    onClick = { onClick(chat.id) },
                    onLongClick = { onLongClick(chat.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    activity: ChatActivity?,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.home_select),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = if (selected) ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else groupedListColors(),
        modifier = modifier,
        leadingContent = {
            when {
                selected -> ShapeBadge(icon = MaterialSymbols.Rounded.Check)
                activity != null -> WorkingBadge(activity)
                else -> ChatBadge(ChatLook.of(chat.iconKey, chat.colorKey, chat.shapeKey))
            }
        },
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
                RelativeTime.format(chat.updatedAt, LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(chat.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@get:StringRes
val ChatAge.label: Int
    get() = when (this) {
        ChatAge.TODAY -> R.string.home_age_today
        ChatAge.YESTERDAY -> R.string.home_age_yesterday
        ChatAge.THIS_WEEK -> R.string.home_age_this_week
        ChatAge.EARLIER -> R.string.home_age_earlier
    }
