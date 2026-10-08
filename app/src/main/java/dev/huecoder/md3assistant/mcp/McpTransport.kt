package dev.huecoder.md3assistant.mcp

import dev.huecoder.md3assistant.data.settings.HeaderEntry
import dev.huecoder.md3assistant.net.HttpStatusException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class McpException(message: String) : IOException(message)

class McpSessionExpiredException : IOException("MCP session expired")

class McpTransport(
    private val url: String,
    private val headers: List<HeaderEntry>,
    private val client: () -> OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val nextId = AtomicLong(1)

    @Volatile
    var sessionId: String? = null

    @Volatile
    var protocolVersion: String? = null

    suspend fun request(method: String, params: JsonObject? = null): JsonObject {
        val id = nextId.getAndIncrement()
        val body = message(method, params) { put("id", id) }
        val response = post(body) { readResponse(it, id) }
        return response ?: throw McpException("The server closed the stream without answering $method")
    }

    suspend fun notify(method: String, params: JsonObject? = null) {
        post(message(method, params) {}) { null }
    }

    private fun message(method: String, params: JsonObject?, extra: JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            extra()
            put("method", method)
            if (params != null) put("params", params)
        }.toString()

    private suspend fun <T> post(body: String, read: (Response) -> T?): T? {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Accept", ACCEPT)
            .apply {
                headers.forEach { header(it.name, it.value) }
                sessionId?.let { header(SESSION_HEADER, it) }
                protocolVersion?.let { header(VERSION_HEADER, it) }
            }
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client().newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val outcome = runCatching { response.use { handle(it, read) } }
                        if (continuation.isActive) outcome.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                    }
                },
            )
        }
    }

    private fun <T> handle(response: Response, read: (Response) -> T?): T? {
        response.header(SESSION_HEADER)?.let { sessionId = it }
        if (response.code == HTTP_NOT_FOUND && sessionId != null) throw McpSessionExpiredException()
        if (!response.isSuccessful) throw HttpStatusException(response.code, response.body.string().take(ERROR_EXCERPT_CHARS))
        if (response.code == HTTP_ACCEPTED) return null
        return read(response)
    }

    private fun readResponse(response: Response, id: Long): JsonObject? {
        val isStream = response.body.contentType()?.let { it.type == "text" && it.subtype == "event-stream" } == true
        if (!isStream) return matching(json.parseToJsonElement(response.body.string()), id)
        val source = response.body.source()
        val data = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: return null
            when {
                line.startsWith(DATA_PREFIX) -> data.append(line.removePrefix(DATA_PREFIX).removePrefix(" ")).append('\n')
                line.isEmpty() && data.isNotEmpty() -> {
                    val event = runCatching { json.parseToJsonElement(data.toString()) }.getOrNull()
                    data.clear()
                    event?.let { matching(it, id) }?.let { return it }
                }
            }
        }
    }

    private fun matching(element: JsonElement, id: Long): JsonObject? {
        val messages = (element as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOfNotNull(element as? JsonObject)
        val reply = messages.firstOrNull { (it["id"] as? JsonPrimitive)?.contentOrNull == id.toString() } ?: return null
        (reply["error"] as? JsonObject)?.let { error ->
            val message = (error["message"] as? JsonPrimitive)?.contentOrNull ?: "Unknown error"
            val code = error["code"]?.jsonPrimitive?.intOrNull
            throw McpException(if (code != null) "$message ($code)" else message)
        }
        return reply["result"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private companion object {
        const val JSONRPC_VERSION = "2.0"
        const val ACCEPT = "application/json, text/event-stream"
        const val SESSION_HEADER = "Mcp-Session-Id"
        const val VERSION_HEADER = "MCP-Protocol-Version"
        const val DATA_PREFIX = "data:"
        const val HTTP_ACCEPTED = 202
        const val HTTP_NOT_FOUND = 404
        const val ERROR_EXCERPT_CHARS = 300
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
