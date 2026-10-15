package dev.huecoder.md3agent.llm.wire

import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.ProviderEndpoint
import dev.huecoder.md3agent.llm.ReasoningBlock
import dev.huecoder.md3agent.llm.ReasoningTrace
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.llm.models.ModelOutput
import dev.huecoder.md3agent.llm.models.ReasoningControl
import dev.huecoder.md3agent.llm.promptFingerprint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object OpenAiChatWire : ChatWire {
    private const val CHAT_COMPLETIONS_PATH = "chat/completions"

    override fun request(endpoint: ProviderEndpoint, chat: ChatRequest): Request = endpoint
        .authorizedRequest(CHAT_COMPLETIONS_PATH)
        .header("Accept", "text/event-stream")
        .post(body(chat).toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    override fun newStream() = ChatStream(::chunkEvents)

    private fun chunkEvents(chunk: JsonObject): List<CompletionEvent> {
        val events = mutableListOf<CompletionEvent>()
        val choice = (chunk["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        if (choice != null) events += choiceEvents(choice)
        chunk.obj("usage")?.let(::usageOf)?.let { events += it }
        return events
    }

    private fun choiceEvents(choice: JsonObject): List<CompletionEvent> {
        val delta = choice.obj("delta")
        val events = mutableListOf<CompletionEvent>()
        delta?.nonEmptyText("reasoning_content", "reasoning")?.let { events += CompletionEvent.ReasoningDelta(it) }
        delta?.nonEmptyText("content")?.let { events += CompletionEvent.ContentDelta(it) }
        (delta?.get("reasoning_details") as? JsonArray)?.forEach { detail ->
            (detail as? JsonObject)?.let { events += CompletionEvent.ReasoningBlockDone(ReasoningBlock(it)) }
        }
        (delta?.get("content") as? JsonArray)?.forEach { part -> (part as? JsonObject)?.let { events += contentPartEvents(it) } }
        (delta?.get("images") as? JsonArray)?.forEach { part -> (part as? JsonObject)?.let { events += contentPartEvents(it) } }
        (delta?.get("tool_calls") as? JsonArray)?.forEachIndexed { position, element ->
            (element as? JsonObject)?.let { events += toolCallDelta(it, position) }
        }
        (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull?.let { events += CompletionEvent.Finished(it) }
        return events
    }

    private fun contentPartEvents(part: JsonObject): List<CompletionEvent> {
        val text = (part["text"] as? JsonPrimitive)?.contentOrNull
        val imageUrl = part.obj("image_url")?.get("url") ?: part["image_url"] ?: part["url"]
        val image = (imageUrl as? JsonPrimitive)?.contentOrNull
            ?: part.text("b64_json")?.let { InlineData.url("image/png", it) }
        return listOfNotNull(
            text?.takeIf { it.isNotEmpty() && part.isTextPart() }?.let(CompletionEvent::ContentDelta),
            image?.let(CompletionEvent::ImageOutput),
        )
    }

    private fun JsonObject.isTextPart() = text("type").let { it == null || it == "text" || it == "output_text" }

    private fun usageOf(usage: JsonObject): CompletionEvent.Usage? {
        val prompt = usage.int("prompt_tokens") ?: return null
        val completion = usage.int("completion_tokens") ?: return null
        val cached = usage.obj("prompt_tokens_details")?.int("cached_tokens")
            ?: usage.int("prompt_cache_hit_tokens") ?: usage.int("cache_read_input_tokens") ?: 0
        return CompletionEvent.Usage(TokenUsage(prompt, completion, cached))
    }

    private fun toolCallDelta(call: JsonObject, position: Int): CompletionEvent.ToolCallDelta {
        val function = call.obj("function")
        return CompletionEvent.ToolCallDelta(
            index = (call["index"] as? JsonPrimitive)?.intOrNull ?: position,
            id = (call["id"] as? JsonPrimitive)?.contentOrNull,
            name = (function?.get("name") as? JsonPrimitive)?.contentOrNull,
            arguments = function?.get("arguments")?.let { arguments ->
                (arguments as? JsonPrimitive)?.takeIf { it.isString }?.content ?: arguments.toString()
            },
        )
    }

    private fun JsonObject.nonEmptyText(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        text(name)?.takeIf { it.isNotEmpty() }
    }

    private fun body(chat: ChatRequest): JsonObject = buildJsonObject {
        val options = chat.options
        put("model", chat.model)
        put("stream", true)
        options.reasoningEffort?.let { effort ->
            when (options.reasoningControl) {
                ReasoningControl.EFFORT_FIELD -> put("reasoning_effort", effort.wireName)
                ReasoningControl.REASONING_OBJECT -> putJsonObject("reasoning") { put("effort", effort.wireName) }
            }
        }
        when (options.output) {
            ModelOutput.TEXT -> Unit
            ModelOutput.IMAGE -> putJsonArray("modalities") { add("image") }
            ModelOutput.TEXT_AND_IMAGE -> putJsonArray("modalities") { add("image"); add("text") }
        }
        putJsonObject("stream_options") { put("include_usage", true) }
        val fingerprint = promptFingerprint(chat.turns, chat.tools)
        val replay = chat.turns.mapNotNull { it.reasoning }.all { it.matches(ApiFormat.OPENAI, chat.model, fingerprint) }
        putJsonArray("messages") {
            chat.turns.forEach { add(message(it, options.explicitCaching && it.cacheBreakpoint, it.reasoning.takeIf { replay })) }
        }
        if (chat.tools.isNotEmpty()) {
            putJsonArray("tools") {
                chat.tools.forEach { tool ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        }
                    }
                }
            }
        }
    }

    private fun message(turn: ChatTurn, markCache: Boolean, trace: ReasoningTrace?): JsonObject = buildJsonObject {
        put("role", turn.role.wireName)
        when {
            turn.imageUrls.isNotEmpty() || (markCache && turn.content.isNotEmpty()) -> putJsonArray("content") {
                if (turn.content.isNotEmpty()) {
                    addJsonObject {
                        put("type", "text")
                        put("text", turn.content)
                        if (markCache && turn.imageUrls.isEmpty()) putJsonObject("cache_control") { put("type", "ephemeral") }
                    }
                }
                turn.imageUrls.forEachIndexed { index, url ->
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") { put("url", url) }
                        if (markCache && index == turn.imageUrls.lastIndex) putJsonObject("cache_control") { put("type", "ephemeral") }
                    }
                }
            }
            turn.toolCalls.isNotEmpty() && turn.content.isEmpty() -> put("content", JsonNull)
            else -> put("content", turn.content)
        }
        if (turn.toolCalls.isNotEmpty()) {
            putJsonArray("tool_calls") {
                turn.toolCalls.forEach { call ->
                    addJsonObject {
                        put("id", call.id)
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", call.name)
                            put("arguments", call.arguments)
                        }
                    }
                }
            }
        }
        trace?.let { replayed -> putJsonArray("reasoning_details") { replayed.blocks.forEach { add(it.json) } } }
        turn.toolCallId?.let { put("tool_call_id", it) }
    }
}
