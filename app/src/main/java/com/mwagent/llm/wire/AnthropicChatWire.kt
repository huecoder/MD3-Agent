package com.mwagent.llm.wire

import com.mwagent.data.settings.ApiFormat
import com.mwagent.llm.ChatTurn
import com.mwagent.llm.CompletionEvent
import com.mwagent.llm.ProviderEndpoint
import com.mwagent.llm.ReasoningBlock
import com.mwagent.llm.ReasoningTrace
import com.mwagent.llm.TokenUsage
import com.mwagent.llm.models.ReasoningEffort
import com.mwagent.llm.promptFingerprint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object AnthropicChatWire : ChatWire {
    private const val MESSAGES_PATH = "messages"
    private const val OUTPUT_TOKENS = 8_000
    private const val ROLE_USER = "user"
    private const val ROLE_ASSISTANT = "assistant"
    private const val THINKING = "thinking"
    private const val REDACTED_THINKING = "redacted_thinking"

    override fun request(endpoint: ProviderEndpoint, chat: ChatRequest): Request = endpoint
        .authorizedRequest(MESSAGES_PATH)
        .header("Accept", "text/event-stream")
        .post(body(chat).toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    override fun newStream(): ChatStream = AnthropicStream()

    private class Message(val role: String, val blocks: MutableList<JsonObject>)

    private fun body(chat: ChatRequest): JsonObject = buildJsonObject {
        val replay = canReplay(chat)
        val budget = chat.options.reasoningEffort?.let(::thinkingBudget)?.takeUnless { midToolLoop(chat.turns) && !continuesThinking(chat.turns, replay) }
        put("model", chat.model)
        put("stream", true)
        put("max_tokens", OUTPUT_TOKENS + (budget ?: 0))
        budget?.let { putJsonObject("thinking") { put("type", "enabled"); put("budget_tokens", it) } }
        val system = chat.turns.filter { it.role == ChatTurn.Role.SYSTEM }.joinToString("\n\n") { it.content }
        if (system.isNotBlank()) {
            putJsonArray("system") {
                addJsonObject {
                    put("type", "text")
                    put("text", system)
                    putCacheControl()
                }
            }
        }
        put("messages", JsonArray(messages(chat.turns, replay).map { message ->
            buildJsonObject {
                put("role", message.role)
                put("content", JsonArray(message.blocks))
            }
        }))
        if (chat.tools.isNotEmpty()) {
            putJsonArray("tools") {
                chat.tools.forEach { tool ->
                    addJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("input_schema", tool.parameters)
                    }
                }
            }
        }
    }

    private fun canReplay(chat: ChatRequest): Boolean {
        val fingerprint = promptFingerprint(chat.turns, chat.tools)
        return chat.turns.mapNotNull { it.reasoning }.all { it.matches(ApiFormat.ANTHROPIC, chat.model, fingerprint) }
    }

    private fun messages(turns: List<ChatTurn>, replay: Boolean): List<Message> {
        val messages = mutableListOf<Message>()
        turns.filter { it.role != ChatTurn.Role.SYSTEM }.forEach { turn ->
            val blocks = blocksOf(turn, turn.reasoning.takeIf { replay }).ifEmpty { return@forEach }
            if (turn.cacheBreakpoint) blocks[blocks.lastIndex] = blocks.last().withCacheControl()
            val role = if (turn.role == ChatTurn.Role.ASSISTANT) ROLE_ASSISTANT else ROLE_USER
            val previous = messages.lastOrNull()
            if (previous?.role == role) previous.blocks += blocks else messages += Message(role, blocks)
        }
        return messages
    }

    private fun blocksOf(turn: ChatTurn, trace: ReasoningTrace?): MutableList<JsonObject> {
        val blocks = mutableListOf<JsonObject>()
        if (turn.role == ChatTurn.Role.TOOL) {
            blocks += buildJsonObject {
                put("type", "tool_result")
                put("tool_use_id", turn.toolCallId.orEmpty())
                put("content", turn.content)
            }
            return blocks
        }
        trace?.let { blocks += it.leading() }
        if (turn.content.isNotBlank()) blocks += buildJsonObject { put("type", "text"); put("text", turn.content) }
        turn.imageUrls.forEach { blocks += imageBlock(it) }
        turn.toolCalls.forEach { call ->
            trace?.let { blocks += it.before(call.id) }
            blocks += buildJsonObject {
                put("type", "tool_use")
                put("id", call.id)
                put("name", call.name)
                put("input", argumentsObject(call.arguments))
            }
        }
        return blocks
    }

    private fun imageBlock(url: String) = buildJsonObject {
        put("type", "image")
        putJsonObject("source") {
            val inline = InlineData.parse(url)
            if (inline != null) {
                put("type", "base64")
                put("media_type", inline.mimeType)
                put("data", inline.base64)
            } else {
                put("type", "url")
                put("url", url)
            }
        }
    }

    private fun midToolLoop(turns: List<ChatTurn>): Boolean =
        turns.lastOrNull { it.role == ChatTurn.Role.ASSISTANT }?.toolCalls?.isNotEmpty() == true

    private fun continuesThinking(turns: List<ChatTurn>, replay: Boolean): Boolean =
        replay && turns.lastOrNull { it.role == ChatTurn.Role.ASSISTANT }?.reasoning?.leading()?.isNotEmpty() == true

    private fun thinkingBudget(effort: ReasoningEffort): Int? = when (effort) {
        ReasoningEffort.NONE -> null
        ReasoningEffort.MINIMAL -> 1_024
        ReasoningEffort.LOW -> 2_048
        ReasoningEffort.MEDIUM -> 8_192
        ReasoningEffort.HIGH -> 16_384
        ReasoningEffort.XHIGH -> 24_000
    }

    private fun JsonObject.withCacheControl() = JsonObject(this + ("cache_control" to cacheControl))

    private fun JsonObjectBuilder.putCacheControl() = put("cache_control", cacheControl)

    private val cacheControl = buildJsonObject { put("type", "ephemeral") }

    private class OpenBlock(val type: String, var text: String, var signature: String, val data: String) {
        fun finish(): JsonObject = buildJsonObject {
            put("type", type)
            if (type == REDACTED_THINKING) {
                put("data", data)
            } else {
                put("thinking", text)
                put("signature", signature)
            }
        }
    }

    private class AnthropicStream : ChatStream {
        private var inputTokens = 0
        private var cacheReadTokens = 0
        private var cacheWriteTokens = 0
        private val open = HashMap<Int, OpenBlock>()
        private val late = mutableListOf<JsonObject>()
        private var bodyStarted = false

        override fun events(chunk: JsonObject): List<CompletionEvent> = when (chunk.text("type")) {
            "message_start" -> {
                chunk.obj("message")?.obj("usage")?.let(::rememberInput)
                emptyList()
            }
            "content_block_start" -> blockStart(chunk.int("index") ?: 0, chunk.obj("content_block"))
            "content_block_delta" -> blockDelta(chunk.int("index") ?: 0, chunk.obj("delta"))
            "content_block_stop" -> blockStop(chunk.int("index") ?: 0)
            "message_delta" -> messageDelta(chunk)
            else -> emptyList()
        }

        private fun rememberInput(usage: JsonObject) {
            usage.int("input_tokens")?.let { inputTokens = it }
            usage.int("cache_read_input_tokens")?.let { cacheReadTokens = it }
            usage.int("cache_creation_input_tokens")?.let { cacheWriteTokens = it }
        }

        private fun blockStart(index: Int, block: JsonObject?): List<CompletionEvent> {
            if (block == null) return emptyList()
            return when (val type = block.text("type").orEmpty()) {
                "tool_use" -> {
                    bodyStarted = true
                    val id = block.text("id")
                    val anchored = late.map { CompletionEvent.ReasoningBlockDone(ReasoningBlock(it, anchor = id)) }
                    late.clear()
                    anchored + CompletionEvent.ToolCallDelta(index, id, block.text("name"), arguments = null)
                }
                "text" -> {
                    bodyStarted = true
                    listOfNotNull(block.text("text")?.takeIf { it.isNotEmpty() }?.let(CompletionEvent::ContentDelta))
                }
                THINKING, REDACTED_THINKING -> {
                    open[index] = OpenBlock(type, block.text("thinking").orEmpty(), block.text("signature").orEmpty(), block.text("data").orEmpty())
                    emptyList()
                }
                else -> emptyList()
            }
        }

        private fun blockStop(index: Int): List<CompletionEvent> {
            val finished = open.remove(index)?.finish() ?: return emptyList()
            if (bodyStarted) {
                late += finished
                return emptyList()
            }
            return listOf(CompletionEvent.ReasoningBlockDone(ReasoningBlock(finished)))
        }

        private fun blockDelta(index: Int, delta: JsonObject?): List<CompletionEvent> = listOfNotNull(
            when (delta?.text("type")) {
                "text_delta" -> delta.text("text")?.let(CompletionEvent::ContentDelta)
                "thinking_delta" -> delta.text("thinking")?.also { text -> open[index]?.let { it.text += text } }?.let(CompletionEvent::ReasoningDelta)
                "signature_delta" -> {
                    delta.text("signature")?.let { part -> open[index]?.let { it.signature += part } }
                    null
                }
                "input_json_delta" -> delta.text("partial_json")?.let { CompletionEvent.ToolCallDelta(index, id = null, name = null, arguments = it) }
                else -> null
            },
        )

        private fun messageDelta(chunk: JsonObject): List<CompletionEvent> {
            val usage = chunk.obj("usage")
            usage?.let(::rememberInput)
            val output = usage?.int("output_tokens") ?: 0
            val prompt = inputTokens + cacheReadTokens + cacheWriteTokens
            return listOfNotNull(
                CompletionEvent.Usage(TokenUsage(prompt, output, cacheReadTokens)),
                chunk.obj("delta")?.text("stop_reason")?.let(CompletionEvent::Finished),
            )
        }
    }
}
