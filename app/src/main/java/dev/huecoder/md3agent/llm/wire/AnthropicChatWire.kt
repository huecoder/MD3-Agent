package dev.huecoder.md3agent.llm.wire

import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.ProviderEndpoint
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.llm.models.ReasoningEffort
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

    override fun request(endpoint: ProviderEndpoint, chat: ChatRequest): Request = endpoint
        .authorizedRequest(MESSAGES_PATH)
        .header("Accept", "text/event-stream")
        .post(body(chat).toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    override fun newStream(): ChatStream = AnthropicStream()

    private class Message(val role: String, val blocks: MutableList<JsonObject>)

    private fun body(chat: ChatRequest): JsonObject = buildJsonObject {
        val budget = chat.options.reasoningEffort?.let(::thinkingBudget)?.takeUnless { midToolLoop(chat.turns) }
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
        put("messages", JsonArray(messages(chat.turns).map { message ->
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

    private fun messages(turns: List<ChatTurn>): List<Message> {
        val messages = mutableListOf<Message>()
        turns.filter { it.role != ChatTurn.Role.SYSTEM }.forEach { turn ->
            val blocks = blocksOf(turn).ifEmpty { return@forEach }
            if (turn.cacheBreakpoint) blocks[blocks.lastIndex] = blocks.last().withCacheControl()
            val role = if (turn.role == ChatTurn.Role.ASSISTANT) ROLE_ASSISTANT else ROLE_USER
            val previous = messages.lastOrNull()
            if (previous?.role == role) previous.blocks += blocks else messages += Message(role, blocks)
        }
        return messages
    }

    private fun blocksOf(turn: ChatTurn): MutableList<JsonObject> {
        val blocks = mutableListOf<JsonObject>()
        if (turn.role == ChatTurn.Role.TOOL) {
            blocks += buildJsonObject {
                put("type", "tool_result")
                put("tool_use_id", turn.toolCallId.orEmpty())
                put("content", turn.content)
            }
            return blocks
        }
        if (turn.content.isNotBlank()) blocks += buildJsonObject { put("type", "text"); put("text", turn.content) }
        turn.imageUrls.forEach { blocks += imageBlock(it) }
        turn.toolCalls.forEach { call ->
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

    private class AnthropicStream : ChatStream {
        private var inputTokens = 0
        private var cacheReadTokens = 0
        private var cacheWriteTokens = 0

        override fun events(chunk: JsonObject): List<CompletionEvent> = when (chunk.text("type")) {
            "message_start" -> {
                chunk.obj("message")?.obj("usage")?.let(::rememberInput)
                emptyList()
            }
            "content_block_start" -> blockStart(chunk.int("index") ?: 0, chunk.obj("content_block"))
            "content_block_delta" -> blockDelta(chunk.int("index") ?: 0, chunk.obj("delta"))
            "message_delta" -> messageDelta(chunk)
            else -> emptyList()
        }

        private fun rememberInput(usage: JsonObject) {
            usage.int("input_tokens")?.let { inputTokens = it }
            usage.int("cache_read_input_tokens")?.let { cacheReadTokens = it }
            usage.int("cache_creation_input_tokens")?.let { cacheWriteTokens = it }
        }

        private fun blockStart(index: Int, block: JsonObject?): List<CompletionEvent> = when (block?.text("type")) {
            "tool_use" -> listOf(CompletionEvent.ToolCallDelta(index, block.text("id"), block.text("name"), arguments = null))
            "text" -> listOfNotNull(block.text("text")?.takeIf { it.isNotEmpty() }?.let(CompletionEvent::ContentDelta))
            else -> emptyList()
        }

        private fun blockDelta(index: Int, delta: JsonObject?): List<CompletionEvent> = listOfNotNull(
            when (delta?.text("type")) {
                "text_delta" -> delta.text("text")?.let(CompletionEvent::ContentDelta)
                "thinking_delta" -> delta.text("thinking")?.let(CompletionEvent::ReasoningDelta)
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
