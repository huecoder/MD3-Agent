package dev.huecoder.md3assistant.feature.chat

import dev.huecoder.md3assistant.conversation.StreamingDraft
import dev.huecoder.md3assistant.conversation.TurnAssembler
import dev.huecoder.md3assistant.data.attachments.Attachment
import dev.huecoder.md3assistant.data.db.MessageEntity
import dev.huecoder.md3assistant.data.db.MessageRole
import dev.huecoder.md3assistant.data.db.MessageStatus
import dev.huecoder.md3assistant.llm.TokenUsage
import dev.huecoder.md3assistant.llm.ToolCall
import dev.huecoder.md3assistant.tools.ReplyUi
import dev.huecoder.md3assistant.tools.ShowUiTool
import dev.huecoder.md3assistant.tools.web.ReadWebpageTool
import dev.huecoder.md3assistant.tools.web.SearchResults
import dev.huecoder.md3assistant.tools.web.WebSearchTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URI

enum class ToolStatus { RUNNING, DONE, ERROR, CANCELLED }

data class ToolCallUi(val id: String, val name: String, val arguments: JsonObject, val status: ToolStatus, val result: String?)

data class SourceUi(val title: String, val url: String, val host: String)

sealed interface ChatItem {
    val id: String

    data class User(override val id: String, val content: String, val attachments: List<Attachment>) : ChatItem

    data class Reply(
        override val id: String,
        val parts: List<ReplyPart>,
        val isActive: Boolean,
        val isWaiting: Boolean,
        val startedAt: Long,
        val finishedAt: Long?,
        val usage: TokenUsage?,
        val end: ReplyEnd?,
        val anchor: MessageEntity,
    ) : ChatItem
}

sealed interface ReplyPart {
    data class Reasoning(val text: String, val isStreaming: Boolean) : ReplyPart

    data class Text(val content: String) : ReplyPart

    data class Tools(val calls: List<ToolCallUi>) : ReplyPart

    data class Error(val text: String) : ReplyPart
}

data class ReplyEnd(
    val sources: List<SourceUi>,
    val ui: ReplyUi,
    val fullText: String,
    val isLatest: Boolean,
)

object ChatTimeline {
    private const val MAX_SOURCES = 8
    private val json = Json { ignoreUnknownKeys = true }

    fun build(messages: List<MessageEntity>, draft: StreamingDraft?): List<ChatItem> {
        val results = messages.filter { it.role == MessageRole.TOOL }.associateBy { it.toolCallId }
        val visible = messages.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        val items = mutableListOf<ChatItem>()
        val pendingRounds = mutableListOf<MessageEntity>()

        fun flushReply(isLatest: Boolean) {
            if (pendingRounds.isEmpty()) return
            items += reply(pendingRounds.toList(), results, draft, isLatest)
            pendingRounds.clear()
        }

        for (row in visible) {
            if (row.role == MessageRole.ASSISTANT) {
                pendingRounds += row
            } else {
                flushReply(isLatest = false)
                items += ChatItem.User(row.id, row.content, Attachment.decode(row.attachmentsJson))
            }
        }
        flushReply(isLatest = true)
        return items
    }

    private fun reply(rounds: List<MessageEntity>, results: Map<String?, MessageEntity>, draft: StreamingDraft?, isLatest: Boolean): ChatItem.Reply {
        val live = draft?.takeIf { d -> rounds.any { it.id == d.messageId } }
        val views = rounds.map { RoundView.of(it, live) }
        val last = views.last()
        val isActive = draft != null && isLatest
        val usages = rounds.mapNotNull { row ->
            if (row.promptTokens != null && row.completionTokens != null) TokenUsage(row.promptTokens, row.completionTokens) else null
        } + listOfNotNull(live?.usage)
        return ChatItem.Reply(
            id = rounds.first().id,
            parts = views.flatMap { it.parts(results, isLive = it.row.id == live?.messageId) }.mergeAdjacentTools(),
            isActive = isActive,
            isWaiting = isActive && last.content.isEmpty() && last.reasoning.isEmpty() && last.calls.isEmpty(),
            startedAt = rounds.first().createdAt,
            finishedAt = if (isActive) null else rounds.last().finishedAt,
            usage = usages.reduceOrNull(TokenUsage::plus),
            end = if (isActive) null else replyEnd(rounds, results, isLatest),
            anchor = rounds.first(),
        )
    }

    private class RoundView(val row: MessageEntity, val content: String, val reasoning: String, val calls: List<ToolCall>) {
        fun parts(results: Map<String?, MessageEntity>, isLive: Boolean): List<ReplyPart> = buildList {
            if (reasoning.isNotEmpty()) add(ReplyPart.Reasoning(reasoning, isStreaming = isLive && content.isEmpty() && calls.isEmpty()))
            if (content.isNotEmpty()) add(ReplyPart.Text(content))
            val shown = calls.filter { it.name != ShowUiTool.NAME }
            if (shown.isNotEmpty()) add(ReplyPart.Tools(shown.map { it.toUi(results[it.id], isLive) }))
            when {
                isLive -> Unit
                row.status == MessageStatus.ERROR -> add(ReplyPart.Error(row.errorText ?: "Неизвестная ошибка"))
                row.status == MessageStatus.COMPLETE && content.isEmpty() && calls.isEmpty() -> add(ReplyPart.Error("Модель вернула пустой ответ"))
            }
        }

        companion object {
            fun of(row: MessageEntity, live: StreamingDraft?): RoundView {
                val draft = live?.takeIf { it.messageId == row.id }
                return RoundView(
                    row = row,
                    content = draft?.content ?: row.content,
                    reasoning = draft?.reasoning ?: row.reasoning.orEmpty(),
                    calls = draft?.toolCalls ?: TurnAssembler.decodeToolCalls(row.toolCallsJson),
                )
            }
        }
    }

    private fun List<ReplyPart>.mergeAdjacentTools(): List<ReplyPart> = fold(mutableListOf()) { merged, part ->
        val previous = merged.lastOrNull()
        if (part is ReplyPart.Tools && previous is ReplyPart.Tools) {
            merged[merged.lastIndex] = ReplyPart.Tools(previous.calls + part.calls)
        } else {
            merged += part
        }
        merged
    }

    private fun ToolCall.toUi(result: MessageEntity?, isLive: Boolean): ToolCallUi {
        val status = when {
            result != null -> if (result.status == MessageStatus.ERROR) ToolStatus.ERROR else ToolStatus.DONE
            isLive -> ToolStatus.RUNNING
            else -> ToolStatus.CANCELLED
        }
        return ToolCallUi(id, name, parseArguments(arguments), status, result?.content)
    }

    private fun replyEnd(rows: List<MessageEntity>, results: Map<String?, MessageEntity>, isLatest: Boolean): ReplyEnd {
        val calls = rows.flatMap { TurnAssembler.decodeToolCalls(it.toolCallsJson) }
        val ui = calls.lastOrNull { it.name == ShowUiTool.NAME }?.let { ReplyUi.parse(it.arguments) } ?: ReplyUi(emptyList(), emptyList())
        return ReplyEnd(
            sources = calls.flatMap { sourcesOf(it, results[it.id]) }.distinctBy { it.url }.take(MAX_SOURCES),
            ui = ui,
            fullText = rows.joinToString("\n\n") { it.content }.trim(),
            isLatest = isLatest,
        )
    }

    private fun sourcesOf(call: ToolCall, result: MessageEntity?): List<SourceUi> {
        if (result == null || result.status == MessageStatus.ERROR) return emptyList()
        return when (call.name) {
            WebSearchTool.NAME -> runCatching { json.decodeFromString(SearchResults.serializer(), result.content) }
                .getOrNull()?.results.orEmpty().mapNotNull { source(it.title, it.url) }
            ReadWebpageTool.NAME -> listOfNotNull(
                (parseArguments(call.arguments)["url"] as? JsonPrimitive)?.content?.let { source(title = it, url = it) },
            )
            else -> emptyList()
        }
    }

    private fun source(title: String, url: String): SourceUi? {
        val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: return null
        return SourceUi(title, url, host)
    }

    private fun parseArguments(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrDefault(JsonObject(emptyMap()))
}
