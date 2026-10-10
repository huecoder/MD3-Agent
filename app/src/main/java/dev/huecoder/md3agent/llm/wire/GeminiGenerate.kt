package dev.huecoder.md3agent.llm.wire

import dev.huecoder.md3agent.llm.ProviderEndpoint
import dev.huecoder.md3agent.llm.ProviderException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object GeminiGenerate {
    private const val GENERATE_METHOD = "generateContent"
    private const val ERROR_PREVIEW_CHARS = 200

    fun request(endpoint: ProviderEndpoint, model: String, body: JsonObject): Request = endpoint
        .authorizedRequest(GeminiChatWire.methodUrl(endpoint, model, GENERATE_METHOD))
        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    fun parts(raw: String): List<JsonObject> {
        val response = runCatching { wireJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: throw ProviderException.MalformedResponse("Gemini returned non-JSON: ${raw.take(ERROR_PREVIEW_CHARS)}")
        response.obj("promptFeedback")?.text("blockReason")?.let { throw ProviderException.MalformedResponse("Blocked by Gemini: $it") }
        val candidate = (response["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw ProviderException.MalformedResponse("Gemini response has no candidates")
        return (candidate.obj("content")?.get("parts") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    fun text(parts: List<JsonObject>): String =
        parts.filterNot { it.flag("thought") }.mapNotNull { it.text("text") }.joinToString("").trim()

    fun inlineData(parts: List<JsonObject>): InlineData? = parts.firstNotNullOfOrNull { part ->
        val data = part.obj("inlineData") ?: return@firstNotNullOfOrNull null
        val mimeType = data.text("mimeType") ?: return@firstNotNullOfOrNull null
        data.text("data")?.let { InlineData(mimeType, it) }
    }
}
