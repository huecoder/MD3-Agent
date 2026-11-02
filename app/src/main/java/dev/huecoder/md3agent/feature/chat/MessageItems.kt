package dev.huecoder.md3agent.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Call_split
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Error
import com.composables.icons.materialsymbols.rounded.Refresh
import com.composables.icons.materialsymbols.rounded.Stop_circle
import com.composables.icons.materialsymbols.rounded.Volume_up
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.chat.diagram.DiagramCard
import dev.huecoder.md3agent.data.settings.TypingStyle
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.ui.avatar.AvatarHandoff
import dev.huecoder.md3agent.ui.components.AgentAvatar
import dev.huecoder.md3agent.ui.components.ShimmerText
import dev.huecoder.md3agent.ui.components.copyText
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.ui.theme.LayoutMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

private val UserBubbleShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp)
private val MaxUserBubbleWidth = 320.dp
private val AvatarSize = 28.dp
private val ActionTouchSize = 36.dp
private val ActionIconSize = 18.dp
private const val DISABLED_ACTION_ALPHA = 0.38f

@Composable
fun UserMessage(
    message: ChatItem.User,
    modifier: Modifier = Modifier,
    onEdit: ((ChatItem.User) -> Unit)? = null,
    onSelectVersion: ((String) -> Unit)? = null,
) {
    var actionsShown by rememberSaveable(message.id) { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.attachments.isNotEmpty()) AttachmentStrip(message.attachments)
        if (message.content.isNotEmpty() || message.versions != null) {
            Column(horizontalAlignment = Alignment.End) {
                if (message.content.isNotEmpty()) {
                    Surface(
                        onClick = { actionsShown = !actionsShown },
                        shape = UserBubbleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.widthIn(max = MaxUserBubbleWidth),
                    ) {
                        FoldableText(
                            text = message.content,
                            stateKey = message.id,
                            fadeColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = actionsShown || message.versions != null,
                    enter = expandVertically(LayoutMotion.settle()) + fadeIn(),
                    exit = shrinkVertically(LayoutMotion.settle()) + fadeOut(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        message.versions?.let { VersionSwitcher(it, onSelectVersion) }
                        AnimatedVisibility(
                            visible = actionsShown,
                            enter = expandHorizontally(LayoutMotion.settle()) + fadeIn(),
                            exit = shrinkHorizontally(LayoutMotion.settle()) + fadeOut(),
                        ) {
                            Row {
                                ActionIcon(MaterialSymbols.Rounded.Content_copy, stringResource(R.string.chat_copy)) {
                                    scope.launch { clipboard.copyText(message.content) }
                                    actionsShown = false
                                }
                                onEdit?.let { edit ->
                                    ActionIcon(MaterialSymbols.Rounded.Edit, stringResource(R.string.chat_edit)) {
                                        actionsShown = false
                                        edit(message)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantReply(
    reply: ChatItem.Reply,
    showUsage: Boolean,
    typing: TypingStyle,
    isSpeaking: Boolean,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onToggleSpeech: () -> Unit,
    onSend: (String) -> Unit,
    onApproval: (ToolCallUi, ApprovalDecision) -> Unit,
    modifier: Modifier = Modifier,
    avatarHandoff: AvatarHandoff? = null,
    onFork: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val awaitingAvatar = avatarHandoff?.isActive == true
        AgentAvatar(
            isWorking = reply.isActive && !awaitingAvatar,
            size = AvatarSize,
            modifier = (avatarHandoff?.targetModifier() ?: Modifier).alpha(if (awaitingAvatar) 0f else 1f),
        )
        reply.parts.forEachIndexed { index, part ->
            key(index) {
                when (part) {
                    is ReplyPart.Text -> SelectionContainer { ReplyText(part.content, part.isStreaming, typing, isStopped = part.isStopped) }
                    is ReplyPart.Work -> {
                        WorkBlock(part)
                        part.calls.firstOrNull { it.status == ToolStatus.AWAITING_APPROVAL }?.let { call ->
                            key(call.id) { ApprovalCard(call, onDecision = { onApproval(call, it) }) }
                        }
                    }
                    is ReplyPart.Media -> MediaGallery(part.attachments)
                    is ReplyPart.Chart -> ChartCard(part.spec)
                    is ReplyPart.Diagram -> DiagramCard(part.spec)
                    is ReplyPart.Error -> ErrorNotice(part.text.asString(), onContinue = onContinue.takeIf { reply.end?.isLatest == true })
                }
            }
        }
        if (reply.isWaiting && reply.retry == null) ShimmerText(stringResource(R.string.chat_thinking))
        reply.retry?.let { RetryBanner(it) }
        reply.end?.let { end -> ReplyExtras(end, onSend) }
        ReplyFooter(reply, showUsage, isSpeaking, onRegenerate, onToggleSpeech, onFork)
    }
}

@Composable
private fun ReplyExtras(end: ReplyEnd, onSend: (String) -> Unit) {
    if (end.sources.isNotEmpty()) SourcePills(end.sources)
    if (end.ui.buttons.isNotEmpty()) ReplyButtons(end.ui.buttons, onSend)
    if (end.isLatest && end.ui.suggestions.isNotEmpty()) SuggestionChips(end.ui.suggestions, onSend)
}

@Composable
private fun ReplyFooter(
    reply: ChatItem.Reply,
    showUsage: Boolean,
    isSpeaking: Boolean,
    onRegenerate: () -> Unit,
    onToggleSpeech: () -> Unit,
    onFork: (() -> Unit)?,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val end = reply.end
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (end != null && end.fullText.isNotEmpty()) {
            ActionIcon(MaterialSymbols.Rounded.Content_copy, stringResource(R.string.chat_copy)) { scope.launch { clipboard.copyText(end.fullText) } }
        }
        if (isSpeaking) {
            ActionIcon(MaterialSymbols.Rounded.Stop_circle, stringResource(R.string.chat_stop_speaking), onClick = onToggleSpeech)
        } else if (end != null && end.fullText.isNotEmpty()) {
            ActionIcon(MaterialSymbols.Rounded.Volume_up, stringResource(R.string.chat_speak), onClick = onToggleSpeech)
        }
        if (end != null && end.isLatest) {
            ActionIcon(MaterialSymbols.Rounded.Refresh, stringResource(R.string.chat_regenerate), onClick = onRegenerate)
        }
        if (end != null && onFork != null) {
            ActionIcon(MaterialSymbols.Rounded.Call_split, stringResource(R.string.chat_fork), onClick = onFork)
        }
        val shownUsage = reply.usage?.takeIf { showUsage }
        val meta = listOfNotNull(
            replyDuration(reply)?.let(ReplyMetaFormat::duration)?.asString(),
            shownUsage?.let(ReplyMetaFormat::usage)?.asString(),
            shownUsage?.let(ReplyMetaFormat::cached)?.asString(),
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
private const val RETRY_TICK_MS = 250L
private const val MILLIS_PER_SECOND = 1000.0
private const val RETRY_REASON_LINES = 2

@Composable
internal fun ActionIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(ActionTouchSize)) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ACTION_ALPHA),
            modifier = Modifier.size(ActionIconSize),
        )
    }
}

@Composable
private fun RetryBanner(notice: RetryNotice) {
    val seconds by produceState(secondsLeft(notice), notice) {
        while (true) {
            value = secondsLeft(notice)
            delay(RETRY_TICK_MS)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ShimmerText(
            if (seconds > 0) stringResource(R.string.chat_retry_waiting, seconds, notice.attempt, notice.limit)
            else stringResource(R.string.chat_retry_now, notice.attempt, notice.limit),
        )
        Text(
            notice.reason.asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = RETRY_REASON_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun secondsLeft(notice: RetryNotice): Int =
    ceil((notice.resumesAt - System.currentTimeMillis()).coerceAtLeast(0) / MILLIS_PER_SECOND).toInt()

@Composable
private fun ErrorNotice(text: String, onContinue: (() -> Unit)?) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = if (onContinue == null) 14.dp else 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(MaterialSymbols.Rounded.Error, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (onContinue != null) {
                TextButton(
                    onClick = onContinue,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.chat_error_continue)) }
            }
        }
    }
}
