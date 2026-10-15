package dev.huecoder.md3agent.llm.wire

import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.ProviderEndpoint
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.llm.models.ModelOutput
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

object GeminiChatWire : ChatWire {
    private const val MODEL_PREFIX = "models/"
    private const val ROLE_USER = "user"
    private const val ROLE_MODEL = "model"
    private const val SKIP_SIGNATURE = "skip_thought_signature_validator"
    private const val LEVELED_THINKING_FAMILY = "gemini-3"
    private const val PRO_FAMILY = "pro"
    private const val PRO_MIN_BUDGET = 128

    override fun request(endpoint: ProviderEndpoint, chat: ChatRequest): Request = endpoint
        .authorizedRequest(methodUrl(endpoint, chat.model, "streamGenerateContent").newBuilder().addQueryParameter("alt", "sse").build())
        .header("Accept", "text/event-stream")
        .post(body(chat).toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    override fun newStream(): ChatStream = GeminiStream()

    fun methodUrl(endpoint: ProviderEndpoint, model: String, method: String): HttpUrl =
        endpoint.url("models/${model.removePrefix(MODEL_PREFIX)}:$method")

    private class Content(val role: String, val parts: MutableList<JsonObject>)

    private fun body(chat: ChatRequest): JsonObject = buildJsonObject {
        val system = chat.turns.filter { it.role == ChatTurn.Role.SYSTEM }.joinToString("\n\n") { it.content }
        if (system.isNotBlank()) putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
        put("contents", JsonArray(contents(chat.turns).map { content ->
            buildJsonObject {
                put("role", content.role)
                put("parts", JsonArray(content.parts))
            }
        }))
        if (chat.tools.isNotEmpty()) {
            putJsonArray("tools") {
                addJsonObject {
                    putJsonArray("functionDeclarations") {
                        chat.tools.forEach { tool ->
                            addJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parametersJsonSchema", tool.parameters)
                            }
                        }
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            chat.options.reasoningEffort?.let { putThinking(chat.model, it) }
            when (chat.options.output) {
                ModelOutput.TEXT -> Unit
                ModelOutput.IMAGE -> putJsonArray("responseModalities") { add("IMAGE") }
                ModelOutput.TEXT_AND_IMAGE -> putJsonArray("responseModalities") { add("TEXT"); add("IMAGE") }
            }
        }
    }

    private fun JsonObjectBuilder.putThinking(model: String, effort: ReasoningEffort) = putJsonObject("thinkingConfig") {
        put("includeThoughts", effort != ReasoningEffort.NONE)
        if (model.contains(LEVELED_THINKING_FAMILY)) {
            put("thinkingLevel", if (effort <= ReasoningEffort.LOW) "low" else "high")
        } else {
            val budget = thinkingBudget(effort)
            put("thinkingBudget", if (model.contains(PRO_FAMILY)) budget.coerceAtLeast(PRO_MIN_BUDGET) else budget)
        }
    }

    private fun thinkingBudget(effort: ReasoningEffort): Int = when (effort) {
        ReasoningEffort.NONE -> 0
        ReasoningEffort.MINIMAL -> 512
        ReasoningEffort.LOW -> 1_024
        ReasoningEffort.MEDIUM -> 8_192
        ReasoningEffort.HIGH -> 16_384
        ReasoningEffort.XHIGH -> 24_576
    }

    private fun contents(turns: List<ChatTurn>): List<Content> {
        val toolNames = turns.flatMap { it.toolCalls }.associate { it.id to it.name }
        val contents = mutableListOf<Content>()
        turns.filter { it.role != ChatTurn.Role.SYSTEM }.forEach { turn ->
            val parts = partsOf(turn, toolNames).ifEmpty { return@forEach }
            val role = if (turn.role == ChatTurn.Role.ASSISTANT) ROLE_MODEL else ROLE_USER
            val previous = contents.lastOrNull()
            if (previous?.role == role) previous.parts += parts else contents += Content(role, parts)
        }
        return contents
    }

    private fun partsOf(turn: ChatTurn, toolNames: Map<String, String>): MutableList<JsonObject> {
        val parts = mutableListOf<JsonObject>()
        if (turn.role == ChatTurn.Role.TOOL) {
            val callId = turn.toolCallId.orEmpty()
            parts += buildJsonObject {
                putJsonObject("functionResponse") {
                    put("id", callId)
                    put("name", toolNames[callId].orEmpty())
                    putJsonObject("response") { put("result", turn.content) }
                }
            }
            return parts
        }
        if (turn.content.isNotBlank()) parts += buildJsonObject { put("text", turn.content) }
        turn.imageUrls.forEach { url ->
            val inline = InlineData.parse(url)
            parts += buildJsonObject {
                if (inline != null) {
                    putJsonObject("inlineData") { put("mimeType", inline.mimeType); put("data", inline.base64) }
                } else {
                    put("text", url)
                }
            }
        }
        turn.toolCalls.forEach { call ->
            parts += buildJsonObject {
                putJsonObject("functionCall") {
                    put("id", call.id)
                    put("name", call.name)
                    put("args", argumentsObject(call.arguments))
                }
                put("thoughtSignature", call.signature ?: SKIP_SIGNATURE)
            }
        }
        return parts
    }

    private class GeminiStream : ChatStream {
        private var nextCallIndex = 0

        override fun events(chunk: JsonObject): List<CompletionEvent> {
            chunk.obj("promptFeedback")?.text("blockReason")?.let { throw ProviderException.Http(code = 0, message = "Blocked by Gemini: $it") }
            val events = mutableListOf<CompletionEvent>()
            val candidate = (chunk["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
            (candidate?.obj("content")?.get("parts") as? JsonArray)?.forEach { part -> (part as? JsonObject)?.let { events += partEvents(it) } }
            chunk.obj("usageMetadata")?.let(::usageOf)?.let { events += it }
            candidate?.text("finishReason")?.let { events += CompletionEvent.Finished(it) }
            return events
        }

        private fun partEvents(part: JsonObject): List<CompletionEvent> {
            part.obj("functionCall")?.let { call ->
                return listOf(
                    CompletionEvent.ToolCallDelta(
                        index = nextCallIndex++,
                        id = call.text("id") ?: "call_${UUID.randomUUID()}",
                        name = call.text("name"),
                        arguments = call.obj("args")?.toString() ?: "{}",
                        signature = part.text("thoughtSignature"),
                    ),
                )
            }
            part.obj("inlineData")?.let { data ->
                val mimeType = data.text("mimeType") ?: return emptyList()
                val base64 = data.text("data") ?: return emptyList()
                return listOf(CompletionEvent.ImageOutput(InlineData.url(mimeType, base64)))
            }
            val text = part.text("text")?.takeIf { it.isNotEmpty() } ?: return emptyList()
            return listOf(if (part.flag("thought")) CompletionEvent.ReasoningDelta(text) else CompletionEvent.ContentDelta(text))
        }

        private fun usageOf(usage: JsonObject): CompletionEvent.Usage? {
            val prompt = usage.int("promptTokenCount") ?: return null
            val output = (usage.int("candidatesTokenCount") ?: 0) + (usage.int("thoughtsTokenCount") ?: 0)
            return CompletionEvent.Usage(TokenUsage(prompt, output, usage.int("cachedContentTokenCount") ?: 0))
        }
    }
}
