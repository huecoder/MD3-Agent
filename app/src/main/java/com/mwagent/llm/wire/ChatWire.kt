package com.mwagent.llm.wire

import com.mwagent.data.settings.ApiFormat
import com.mwagent.llm.ChatTurn
import com.mwagent.llm.CompletionEvent
import com.mwagent.llm.CompletionOptions
import com.mwagent.llm.ProviderEndpoint
import com.mwagent.llm.ProviderException
import com.mwagent.llm.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request

data class ChatRequest(
    val model: String,
    val turns: List<ChatTurn>,
    val tools: List<ToolSpec>,
    val options: CompletionOptions,
)

interface ChatWire {
    fun request(endpoint: ProviderEndpoint, chat: ChatRequest): Request

    fun newStream(): ChatStream

    companion object {
        fun of(format: ApiFormat): ChatWire = when (format) {
            ApiFormat.OPENAI -> OpenAiChatWire
            ApiFormat.ANTHROPIC -> AnthropicChatWire
            ApiFormat.GEMINI -> GeminiChatWire
            ApiFormat.ELEVENLABS -> throw ProviderException.InvalidConfiguration("ElevenLabs has no chat API")
        }
    }
}

fun interface ChatStream {
    fun events(chunk: JsonObject): List<CompletionEvent>
}

data class InlineData(val mimeType: String, val base64: String) {
    companion object {
        private const val PREFIX = "data:"
        private const val BASE64_MARKER = ";base64,"

        fun parse(url: String): InlineData? {
            if (!url.startsWith(PREFIX)) return null
            val marker = url.indexOf(BASE64_MARKER).takeIf { it > 0 } ?: return null
            return InlineData(url.substring(PREFIX.length, marker), url.substring(marker + BASE64_MARKER.length))
        }

        fun url(mimeType: String, base64: String) = "$PREFIX$mimeType$BASE64_MARKER$base64"
    }
}

internal val wireJson = Json { ignoreUnknownKeys = true }

internal val JSON_MEDIA_TYPE = "application/json".toMediaType()

internal fun argumentsObject(arguments: String): JsonObject =
    runCatching { wireJson.parseToJsonElement(arguments) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())

internal fun JsonObject.obj(name: String) = this[name] as? JsonObject

internal fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.flag(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull == "true"
