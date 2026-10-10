package dev.huecoder.md3agent.mcp

import dev.huecoder.md3agent.data.settings.HeaderEntry
import dev.huecoder.md3agent.net.HttpStatusException
import dev.huecoder.md3agent.net.executeCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class McpException(message: String) : IOException(message)

class McpSessionExpiredException : IOException("MCP session expired")

class McpUnauthorizedException(val resourceMetadataUrl: String?) : IOException("The server asks you to sign in")

fun interface McpServerRequests {
    suspend fun handle(method: String, params: JsonObject?): JsonObject?

    companion object {
        val NONE = McpServerRequests { _, _ -> null }
    }
}

interface McpAuthorizer {
    suspend fun authorization(): String?

    suspend fun refresh(): Boolean
}

class McpTransport(
    private val url: String,
    private val headers: List<HeaderEntry>,
    private val authorizer: McpAuthorizer?,
    private val client: () -> OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val nextId = AtomicLong(1)

    @Volatile
    var sessionId: String? = null

    @Volatile
    var protocolVersion: String? = null

    suspend fun request(method: String, params: JsonObject? = null, serverRequests: McpServerRequests = McpServerRequests.NONE): JsonObject {
        val id = nextId.getAndIncrement()
        val body = message(method, params) { put("id", id) }
        return exchange(body) { readResponse(it, id, serverRequests) }
            ?: throw McpException("The server closed the stream without answering $method")
    }

    suspend fun notify(method: String, params: JsonObject? = null) {
        exchange(message(method, params) {}) { null }
    }

    private suspend fun respond(id: JsonElement, result: JsonObject?) {
        val body = buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            put("id", id)
            if (result != null) {
                put("result", result)
            } else {
                putJsonObject("error") {
                    put("code", METHOD_NOT_FOUND)
                    put("message", "This client does not support the request")
                }
            }
        }
        exchange(body.toString()) { null }
    }

    private fun message(method: String, params: JsonObject?, extra: JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            extra()
            put("method", method)
            if (params != null) put("params", params)
        }.toString()

    private suspend fun <T> exchange(body: String, read: suspend (Response) -> T?): T? {
        val authorization = authorizer?.authorization()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Accept", ACCEPT)
            .apply {
                headers.forEach { header(it.name, it.value) }
                authorization?.let { header(AUTHORIZATION, it) }
                sessionId?.let { header(SESSION_HEADER, it) }
                protocolVersion?.let { header(VERSION_HEADER, it) }
            }
            .build()
        return withContext(Dispatchers.IO) {
            val call = client().newCall(request)
            try {
                call.executeCancellable { response -> handle(response, read) }
            } catch (e: IOException) {
                if (call.isCanceled()) throw CancellationException("MCP request cancelled", e)
                throw e
            }
        }
    }

    private suspend fun <T> handle(response: Response, read: suspend (Response) -> T?): T? {
        response.header(SESSION_HEADER)?.let { sessionId = it }
        if (response.code == HTTP_UNAUTHORIZED) throw McpUnauthorizedException(resourceMetadataOf(response.header(WWW_AUTHENTICATE)))
        if (response.code == HTTP_NOT_FOUND && sessionId != null) throw McpSessionExpiredException()
        if (!response.isSuccessful) throw HttpStatusException(response.code, response.body.string().take(ERROR_EXCERPT_CHARS))
        if (response.code == HTTP_ACCEPTED) return null
        return read(response)
    }

    private suspend fun readResponse(response: Response, id: Long, serverRequests: McpServerRequests): JsonObject? {
        val isStream = response.body.contentType()?.let { it.type == "text" && it.subtype == "event-stream" } == true
        if (!isStream) return dispatch(json.parseToJsonElement(response.body.string()), id, serverRequests)
        val source = response.body.source()
        val data = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: return null
            when {
                line.startsWith(DATA_PREFIX) -> data.append(line.removePrefix(DATA_PREFIX).removePrefix(" ")).append('\n')
                line.isEmpty() && data.isNotEmpty() -> {
                    val event = runCatching { json.parseToJsonElement(data.toString()) }.getOrNull()
                    data.clear()
                    event?.let { dispatch(it, id, serverRequests) }?.let { return it }
                }
            }
        }
    }

    private suspend fun dispatch(element: JsonElement, id: Long, serverRequests: McpServerRequests): JsonObject? {
        val messages = (element as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOfNotNull(element as? JsonObject)
        for (message in messages) {
            val method = (message["method"] as? JsonPrimitive)?.contentOrNull
            val messageId = message["id"]
            when {
                method != null && messageId != null -> respond(messageId, serverRequests.handle(method, message["params"] as? JsonObject))
                method == null && (messageId as? JsonPrimitive)?.contentOrNull == id.toString() -> return resultOf(message)
            }
        }
        return null
    }

    private fun resultOf(reply: JsonObject): JsonObject {
        (reply["error"] as? JsonObject)?.let { error ->
            val message = (error["message"] as? JsonPrimitive)?.contentOrNull ?: "Unknown error"
            val code = error["code"]?.jsonPrimitive?.intOrNull
            throw McpException(if (code != null) "$message ($code)" else message)
        }
        return reply["result"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun resourceMetadataOf(challenge: String?): String? =
        challenge?.let { RESOURCE_METADATA.find(it)?.groupValues?.get(1) }

    private companion object {
        const val JSONRPC_VERSION = "2.0"
        const val ACCEPT = "application/json, text/event-stream"
        const val AUTHORIZATION = "Authorization"
        const val WWW_AUTHENTICATE = "WWW-Authenticate"
        const val SESSION_HEADER = "Mcp-Session-Id"
        const val VERSION_HEADER = "MCP-Protocol-Version"
        const val DATA_PREFIX = "data:"
        const val HTTP_ACCEPTED = 202
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val METHOD_NOT_FOUND = -32601
        const val ERROR_EXCERPT_CHARS = 300
        val RESOURCE_METADATA = Regex("""resource_metadata="([^"]+)"""")
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
