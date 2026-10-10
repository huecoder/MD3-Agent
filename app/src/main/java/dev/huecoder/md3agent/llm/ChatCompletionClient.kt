package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.llm.wire.ChatRequest
import dev.huecoder.md3agent.llm.wire.ChatStream
import dev.huecoder.md3agent.llm.wire.ChatWire
import dev.huecoder.md3agent.llm.wire.wireJson
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.executeCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Response
import java.io.IOException

class ChatCompletionClient(private val httpClients: HttpClientProvider) {
    fun stream(
        provider: ProviderProfile,
        model: String,
        turns: List<ChatTurn>,
        tools: List<ToolSpec> = emptyList(),
        options: CompletionOptions = CompletionOptions(),
    ): Flow<CompletionEvent> = flow {
        val wire = ChatWire.of(provider.format)
        val request = wire.request(ProviderEndpoint(provider), ChatRequest(model, turns, tools, options))
        val call = httpClients.current().newCall(request)
        try {
            call.executeCancellable { response ->
                if (!response.isSuccessful) throw httpError(response)
                emitServerSentEvents(response, wire.newStream())
            }
        } catch (e: IOException) {
            if (call.isCanceled()) throw CancellationException("Stream cancelled", e)
            throw ProviderException.Network(e)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<CompletionEvent>.emitServerSentEvents(response: Response, stream: ChatStream) {
        val source = response.body.source()
        var finished = false
        var usageSeen = false
        while (!(finished && usageSeen)) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith(SSE_DATA_PREFIX)) continue
            val data = line.removePrefix(SSE_DATA_PREFIX).trim()
            if (data == SSE_DONE) break
            if (data.isEmpty()) continue
            for (event in stream.events(parseChunk(data))) {
                emit(event)
                if (event is CompletionEvent.Finished) finished = true
                if (event is CompletionEvent.Usage) usageSeen = true
            }
        }
        if (!finished) emit(CompletionEvent.Finished(reason = null))
    }

    private fun parseChunk(data: String): JsonObject {
        val chunk = try {
            wireJson.parseToJsonElement(data).jsonObject
        } catch (e: SerializationException) {
            throw ProviderException.MalformedResponse("Could not parse response chunk: ${data.take(ERROR_PREVIEW_CHARS)}")
        } catch (e: IllegalArgumentException) {
            throw ProviderException.MalformedResponse("Response chunk is not a JSON object: ${data.take(ERROR_PREVIEW_CHARS)}")
        }
        chunk["error"]?.let { throw ProviderException.Http(code = 0, message = errorMessageOf(chunk.toString())) }
        return chunk
    }

    private fun httpError(response: Response): ProviderException.Http {
        val body = response.body.string()
        return ProviderException.Http(response.code, "HTTP ${response.code}: ${errorMessageOf(body)}")
    }

    private fun errorMessageOf(body: String): String {
        val parsed = runCatching { wireJson.parseToJsonElement(body) }.getOrNull()
        val root = (parsed as? JsonObject) ?: ((parsed as? JsonArray)?.firstOrNull() as? JsonObject)
        val message = when (val error = root?.get("error")) {
            is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> error.contentOrNull
            else -> (root?.get("message") as? JsonPrimitive)?.contentOrNull
        }
        return message ?: body.take(ERROR_PREVIEW_CHARS).ifBlank { "empty response" }
    }

    private companion object {
        const val SSE_DATA_PREFIX = "data:"
        const val SSE_DONE = "[DONE]"
        const val ERROR_PREVIEW_CHARS = 300
    }
}
