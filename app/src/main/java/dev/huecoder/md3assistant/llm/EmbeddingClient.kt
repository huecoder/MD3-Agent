package dev.huecoder.md3assistant.llm

import dev.huecoder.md3assistant.data.settings.EmbeddingKind
import dev.huecoder.md3assistant.data.settings.MemorySettings
import dev.huecoder.md3assistant.net.HttpClientProvider
import dev.huecoder.md3assistant.net.fetchText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.sqrt

enum class EmbeddingPurpose { DOCUMENT, QUERY }

class EmbeddingClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun embed(texts: List<String>, purpose: EmbeddingPurpose, settings: MemorySettings): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        check(settings.embeddingsReady) { "Embeddings are not configured" }
        val vectors = texts.chunked(MAX_BATCH).flatMap { batch ->
            when (settings.embeddingKind) {
                EmbeddingKind.GEMINI -> gemini(batch, purpose, settings)
                EmbeddingKind.OPENAI -> openAi(batch, settings)
                EmbeddingKind.OFF -> error("Embeddings are turned off")
            }
        }
        check(vectors.size == texts.size) { "Embedding service returned ${vectors.size} vectors for ${texts.size} texts" }
        return vectors.map(::normalized)
    }

    private suspend fun gemini(texts: List<String>, purpose: EmbeddingPurpose, settings: MemorySettings): List<FloatArray> {
        val model = settings.model.removePrefix(MODEL_PREFIX)
        val usesPrefixes = model.startsWith(PREFIXED_GEMINI_FAMILY)
        val body = buildJsonObject {
            putJsonArray("requests") {
                texts.forEach { text ->
                    addJsonObject {
                        put("model", MODEL_PREFIX + model)
                        putJsonObject("content") {
                            putJsonArray("parts") { addJsonObject { put("text", if (usesPrefixes) prefixed(text, purpose) else text) } }
                        }
                        if (!usesPrefixes) put("taskType", if (purpose == EmbeddingPurpose.QUERY) "RETRIEVAL_QUERY" else "RETRIEVAL_DOCUMENT")
                        put("outputDimensionality", GEMINI_DIMENSIONS)
                    }
                }
            }
        }
        val url = endpoint(settings.baseUrl).newBuilder().addPathSegment("models").addPathSegment("$model:batchEmbedContents").build()
        val request = Request.Builder().url(url).header("x-goog-api-key", settings.embeddingApiKey.trim())
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        val response = json.parseToJsonElement(httpClients.current().fetchText(request)).jsonObject
        return response.getValue("embeddings").jsonArray.map { vector(it.jsonObject.getValue("values").jsonArray) }
    }

    private suspend fun openAi(texts: List<String>, settings: MemorySettings): List<FloatArray> {
        val body = buildJsonObject {
            put("model", settings.model)
            put("input", JsonArray(texts.map(::JsonPrimitive)))
        }
        val url = endpoint(settings.baseUrl.removeSuffix("/").removeSuffix("/embeddings")).newBuilder().addPathSegment("embeddings").build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${settings.embeddingApiKey.trim()}")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        val response = json.parseToJsonElement(httpClients.current().fetchText(request)).jsonObject
        return response.getValue("data").jsonArray
            .map { it.jsonObject }
            .sortedBy { it["index"]?.jsonPrimitive?.int ?: 0 }
            .map { vector(it.getValue("embedding").jsonArray) }
    }

    private fun prefixed(text: String, purpose: EmbeddingPurpose) = when (purpose) {
        EmbeddingPurpose.QUERY -> "task: search result | query: $text"
        EmbeddingPurpose.DOCUMENT -> "title: none | text: $text"
    }

    private fun endpoint(raw: String): HttpUrl =
        raw.trim().removeSuffix("/").toHttpUrlOrNull() ?: throw IllegalStateException("Invalid embeddings address: \"$raw\"")

    private fun vector(values: JsonArray) = FloatArray(values.size) { values[it].jsonPrimitive.float }

    private companion object {
        const val MODEL_PREFIX = "models/"
        const val PREFIXED_GEMINI_FAMILY = "gemini-embedding-2"
        const val GEMINI_DIMENSIONS = 768
        const val MAX_BATCH = 32
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal fun normalized(vector: FloatArray): FloatArray {
    val length = sqrt(vector.fold(0f) { sum, value -> sum + value * value })
    return if (length == 0f) vector else FloatArray(vector.size) { vector[it] / length }
}
