package dev.huecoder.md3agent.feature.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.huecoder.md3agent.data.settings.AgentSettings
import dev.huecoder.md3agent.ui.avatar.AgentLook
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.groupedListColors

private const val PRESSED_SCALE = 0.97f
private val PRESS_SPEC = spring<Float>(dampingRatio = 0.6f, stiffness = 900f)
private val FirstSectionGap = 8.dp
private val SectionGap = 24.dp

data class RowSwipes(val right: RowAction?, val left: RowAction?)

@Composable
fun ChatList(
    groups: List<ChatGroup>,
    activity: Map<String, ChatActivity>,
    agents: AgentSettings,
    selection: Set<String>,
    swipes: RowSwipes,
    @StringRes emptyText: Int,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
    onAction: (RowAction, String) -> Unit,
    onSelectRange: (anchor: String, current: String) -> Unit,
    onRangeEnd: () -> Unit,
    header: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val chatIds = groups.flatMap { group -> group.chats.map { it.id } }.toSet()
    val isChat by rememberUpdatedState<(Any) -> Boolean> { key -> key in chatIds }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dragToSelect(listState, scope, isSelectable = { isChat(it) }, onRange = onSelectRange, onEnd = onRangeEnd),
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
                    stringResource(emptyText),
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
                val shapes = ListItemDefaults.segmentedShapes(index = index, count = group.chats.size)
                val selecting = selection.isNotEmpty()
                SwipeableRow(
                    right = swipes.right.takeUnless { selecting },
                    left = swipes.left.takeUnless { selecting },
                    shape = shapes.shape,
                    onAction = { onAction(it, chat.id) },
                    modifier = Modifier.animateItem(),
                ) {
                    ChatRow(
                        chat = chat,
                        activity = activity[chat.id],
                        agentLook = AgentLook.of(agents.forChat(chat.agentId)),
                        selected = chat.id in selection,
                        shapes = shapes,
                        onClick = { onClick(chat.id) },
                        onLongClick = { onLongClick(chat.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    activity: ChatActivity?,
    agentLook: AgentLook,
    selected: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, PRESS_SPEC, label = "rowPress")
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.home_select),
        shapes = shapes,
        colors = if (selected) ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else groupedListColors(),
        interactionSource = interactions,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        leadingContent = {
            when {
                selected -> ShapeBadge(icon = MaterialSymbols.Rounded.Check)
                activity != null -> WorkingBadge(activity, look = agentLook)
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
