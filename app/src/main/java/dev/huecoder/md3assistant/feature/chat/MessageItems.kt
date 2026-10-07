package dev.huecoder.md3assistant.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.composables.icons.materialsymbols.rounded.Error
import com.composables.icons.materialsymbols.rounded.Refresh
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.ui.components.AgentAvatar
import dev.huecoder.md3assistant.ui.components.MarkdownContent
import dev.huecoder.md3assistant.ui.components.copyText
import dev.huecoder.md3assistant.ui.text.asString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val UserBubbleShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp)
private val MaxUserBubbleWidth = 320.dp
private val AvatarSize = 28.dp
private val ActionTouchSize = 36.dp
private val ActionIconSize = 18.dp

@Composable
fun UserMessage(message: ChatItem.User, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.attachments.isNotEmpty()) AttachmentStrip(message.attachments)
        if (message.content.isNotEmpty()) {
            Surface(
                shape = UserBubbleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.widthIn(max = MaxUserBubbleWidth),
            ) {
                SelectionContainer {
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AssistantReply(
    reply: ChatItem.Reply,
    showUsage: Boolean,
    onRegenerate: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AgentAvatar(isWorking = reply.isActive, size = AvatarSize)
        reply.parts.forEachIndexed { index, part ->
            key(index) {
                when (part) {
                    is ReplyPart.Reasoning -> ReasoningBlock(part.text, part.isStreaming)
                    is ReplyPart.Text -> SelectionContainer { MarkdownContent(part.content) }
                    is ReplyPart.Tools -> ToolGroup(part.calls)
                    is ReplyPart.Error -> ErrorNotice(part.text.asString())
                }
            }
        }
        if (reply.isWaiting) ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
        reply.end?.let { end -> ReplyExtras(end, onSend) }
        ReplyFooter(reply, showUsage, onRegenerate)
    }
}

@Composable
private fun ReplyExtras(end: ReplyEnd, onSend: (String) -> Unit) {
    if (end.sources.isNotEmpty()) SourcePills(end.sources)
    if (end.ui.buttons.isNotEmpty()) ReplyButtons(end.ui.buttons, onSend)
    if (end.isLatest && end.ui.suggestions.isNotEmpty()) SuggestionChips(end.ui.suggestions, onSend)
}

@Composable
private fun ReplyFooter(reply: ChatItem.Reply, showUsage: Boolean, onRegenerate: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val end = reply.end
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (end != null && end.fullText.isNotEmpty()) {
            ActionIcon(MaterialSymbols.Rounded.Content_copy, stringResource(R.string.chat_copy)) { scope.launch { clipboard.copyText(end.fullText) } }
        }
        if (end != null && end.isLatest) {
            ActionIcon(MaterialSymbols.Rounded.Refresh, stringResource(R.string.chat_regenerate), onRegenerate)
        }
        val meta = listOfNotNull(
            replyDuration(reply)?.let(ReplyMetaFormat::duration)?.asString(),
            reply.usage?.takeIf { showUsage }?.let(ReplyMetaFormat::usage)?.asString(),
        )
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (end == null) 0.dp else 6.dp),
            )
        }
    }
}

@Composable
private fun replyDuration(reply: ChatItem.Reply): Long? {
    if (!reply.isActive) return reply.finishedAt?.let { it - reply.startedAt }
    val now by produceState(System.currentTimeMillis(), reply.startedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(TIMER_TICK_MS)
        }
    }
    return (now - reply.startedAt).coerceAtLeast(0)
}

private const val TIMER_TICK_MS = 100L

@Composable
private fun ActionIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(ActionTouchSize)) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(ActionIconSize))
    }
}

@Composable
private fun ErrorNotice(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(MaterialSymbols.Rounded.Error, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
