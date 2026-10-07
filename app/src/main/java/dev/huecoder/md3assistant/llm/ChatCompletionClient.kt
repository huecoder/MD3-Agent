package dev.huecoder.md3assistant.llm

import dev.huecoder.md3assistant.data.settings.ProviderProfile
import dev.huecoder.md3assistant.net.HttpClientProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class ChatCompletionClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    fun stream(
        provider: ProviderProfile,
        model: String,
        turns: List<ChatTurn>,
        tools: List<ToolSpec> = emptyList(),
    ): Flow<CompletionEvent> = flow {
        val request = ProviderEndpoint(provider)
            .authorizedRequest(CHAT_COMPLETIONS_PATH)
            .header("Accept", "text/event-stream")
            .post(requestBody(model, turns, tools).toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = httpClients.current().newCall(request)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw httpError(response)
                emitServerSentEvents(response)
            }
        } catch (e: IOException) {
            if (call.isCanceled()) throw CancellationException("Stream cancelled", e)
            throw ProviderException.Network(e)
        } finally {
            cancellation?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<CompletionEvent>.emitServerSentEvents(response: Response) {
        val source = response.body.source()
        var finished = false
        var usageSeen = false
        while (!(finished && usageSeen)) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith(SSE_DATA_PREFIX)) continue
            val data = line.removePrefix(SSE_DATA_PREFIX).trim()
            if (data == SSE_DONE) break
            if (data.isEmpty()) continue
            for (event in parseChunk(data)) {
                emit(event)
                if (event is CompletionEvent.Finished) finished = true
                if (event is CompletionEvent.Usage) usageSeen = true
            }
        }
        if (!finished) emit(CompletionEvent.Finished(reason = null))
    }

    private fun parseChunk(data: String): List<CompletionEvent> {
        val chunk = try {
            json.parseToJsonElement(data).jsonObject
        } catch (e: SerializationException) {
            throw ProviderException.MalformedResponse("Не удалось разобрать чанк ответа: ${data.take(ERROR_PREVIEW_CHARS)}")
        } catch (e: IllegalArgumentException) {
            throw ProviderException.MalformedResponse("Чанк ответа не является объектом: ${data.take(ERROR_PREVIEW_CHARS)}")
        }
        chunk["error"]?.let { throw ProviderException.Http(code = 0, message = errorMessageOf(it.toString())) }

        val events = mutableListOf<CompletionEvent>()
        val choice = (chunk["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        if (choice != null) events += choiceEvents(choice)
        (chunk["usage"] as? JsonObject)?.let(::usageOf)?.let { events += it }
        return events
    }

    private fun choiceEvents(choice: JsonObject): List<CompletionEvent> {
        val delta = choice["delta"] as? JsonObject
        val events = mutableListOf<CompletionEvent>()
        delta?.stringField("reasoning_content", "reasoning")?.let { events += CompletionEvent.ReasoningDelta(it) }
        delta?.stringField("content")?.let { events += CompletionEvent.ContentDelta(it) }
        (delta?.get("tool_calls") as? JsonArray)?.forEachIndexed { position, element ->
            (element as? JsonObject)?.let { events += toolCallDelta(it, position) }
        }
        (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull?.let { events += CompletionEvent.Finished(it) }
        return events
    }

    private fun usageOf(usage: JsonObject): CompletionEvent.Usage? {
        val prompt = (usage["prompt_tokens"] as? JsonPrimitive)?.intOrNull ?: return null
        val completion = (usage["completion_tokens"] as? JsonPrimitive)?.intOrNull ?: return null
        return CompletionEvent.Usage(TokenUsage(prompt, completion))
    }

    private fun toolCallDelta(call: JsonObject, position: Int): CompletionEvent.ToolCallDelta {
        val function = call["function"] as? JsonObject
        return CompletionEvent.ToolCallDelta(
            index = (call["index"] as? JsonPrimitive)?.intOrNull ?: position,
            id = (call["id"] as? JsonPrimitive)?.contentOrNull,
            name = (function?.get("name") as? JsonPrimitive)?.contentOrNull,
            arguments = function?.get("arguments")?.let { arguments ->
                (arguments as? JsonPrimitive)?.takeIf { it.isString }?.content ?: arguments.toString()
            },
        )
    }

    private fun JsonObject.stringField(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
    }

    private fun requestBody(model: String, turns: List<ChatTurn>, tools: List<ToolSpec>): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", true)
        putJsonObject("stream_options") { put("include_usage", true) }
        putJsonArray("messages") { turns.forEach { add(wireMessage(it)) } }
        if (tools.isNotEmpty()) {
            putJsonArray("tools") {
                tools.forEach { tool ->
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

    private fun wireMessage(turn: ChatTurn): JsonObject = buildJsonObject {
        put("role", turn.role.wireName)
        when {
            turn.imageUrls.isNotEmpty() -> putJsonArray("content") {
                if (turn.content.isNotEmpty()) addJsonObject { put("type", "text"); put("text", turn.content) }
                turn.imageUrls.forEach { url ->
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") { put("url", url) }
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
        turn.toolCallId?.let { put("tool_call_id", it) }
    }

    private fun httpError(response: Response): ProviderException.Http {
        val body = response.body.string()
        return ProviderException.Http(response.code, "HTTP ${response.code}: ${errorMessageOf(body)}")
    }

    private fun errorMessageOf(body: String): String {
        val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val error = parsed?.get("error")
        val message = when (error) {
            is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> error.contentOrNull
            else -> (parsed?.get("message") as? JsonPrimitive)?.contentOrNull
        }
        return message ?: body.take(ERROR_PREVIEW_CHARS).ifBlank { "пустой ответ" }
    }

    private companion object {
        const val CHAT_COMPLETIONS_PATH = "chat/completions"
        const val SSE_DATA_PREFIX = "data:"
        const val SSE_DONE = "[DONE]"
        const val ERROR_PREVIEW_CHARS = 300
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
