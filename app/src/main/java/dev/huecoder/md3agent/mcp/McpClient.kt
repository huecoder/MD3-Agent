package dev.huecoder.md3agent.mcp

import dev.huecoder.md3agent.BuildConfig
import dev.huecoder.md3agent.data.settings.McpServer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

data class McpRemoteTool(val name: String, val title: String?, val description: String, val inputSchema: JsonObject)

sealed interface McpContent {
    data class Text(val text: String) : McpContent

    data class Image(val base64: String, val mimeType: String) : McpContent
}

data class McpCallResult(val content: List<McpContent>, val isError: Boolean)

class McpClient(server: McpServer, private val authorizer: McpAuthorizer?, client: () -> OkHttpClient) {
    private val transport = McpTransport(server.url.trim(), server.requestHeaders(), authorizer, client)
    private val initLock = Mutex()

    @Volatile
    private var initialized = false

    @Volatile
    var instructions: String? = null
        private set

    suspend fun listTools(): List<McpRemoteTool> = inSession {
        val tools = mutableListOf<McpRemoteTool>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val params = cursor?.let { buildJsonObject { put("cursor", it) } }
            val page = transport.request("tools/list", params)
            (page["tools"] as? JsonArray).orEmpty().mapNotNullTo(tools) { (it as? JsonObject)?.let(::parseTool) }
            cursor = page.string("nextCursor") ?: return@inSession tools
        }
        tools
    }

    suspend fun callTool(name: String, arguments: JsonObject, serverRequests: McpServerRequests = McpServerRequests.NONE): McpCallResult = inSession {
        val result = transport.request(
            "tools/call",
            buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
            serverRequests,
        )
        parseResult(result)
    }

    private suspend fun <T> inSession(block: suspend () -> T): T = withAuthRetry {
        ensureInitialized()
        try {
            block()
        } catch (e: McpSessionExpiredException) {
            initLock.withLock {
                initialized = false
                transport.sessionId = null
            }
            ensureInitialized()
            block()
        }
    }

    private suspend fun <T> withAuthRetry(block: suspend () -> T): T = try {
        block()
    } catch (e: McpUnauthorizedException) {
        if (authorizer?.refresh() != true) throw e
        block()
    }

    private suspend fun ensureInitialized() = initLock.withLock {
        if (initialized) return@withLock
        val result = transport.request("initialize", buildJsonObject {
            put("protocolVersion", PROTOCOL_VERSION)
            putJsonObject("capabilities") { putJsonObject("elicitation") {} }
            putJsonObject("clientInfo") {
                put("name", CLIENT_NAME)
                put("version", BuildConfig.VERSION_NAME)
            }
        })
        transport.protocolVersion = result.string("protocolVersion") ?: PROTOCOL_VERSION
        instructions = result.string("instructions")?.takeIf { it.isNotBlank() }
        transport.notify("notifications/initialized")
        initialized = true
    }

    private fun parseTool(raw: JsonObject): McpRemoteTool? {
        val name = raw.string("name") ?: return null
        return McpRemoteTool(
            name = name,
            title = raw.string("title") ?: (raw["annotations"] as? JsonObject)?.string("title"),
            description = raw.string("description").orEmpty(),
            inputSchema = raw["inputSchema"] as? JsonObject ?: EMPTY_SCHEMA,
        )
    }

    private fun parseResult(result: JsonObject): McpCallResult {
        val content = (result["content"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseContent) }
        val structured = result["structuredContent"]?.takeIf { content.isEmpty() }?.let { McpContent.Text(it.toString()) }
        val isError = (result["isError"] as? JsonPrimitive)?.booleanOrNull == true
        return McpCallResult(content + listOfNotNull(structured), isError)
    }

    private fun parseContent(item: JsonObject): McpContent? = when (item.string("type")) {
        "text" -> item.string("text")?.let(McpContent::Text)
        "image" -> item.string("data")?.let { McpContent.Image(it, item.string("mimeType") ?: DEFAULT_IMAGE_MIME) }
        "resource" -> (item["resource"] as? JsonObject)?.let { resource ->
            McpContent.Text(resource.string("text") ?: "[resource ${resource.string("uri").orEmpty()}]")
        }
        "resource_link" -> McpContent.Text("[${item.string("name").orEmpty()}](${item.string("uri").orEmpty()})")
        null -> null
        else -> McpContent.Text("[${item.string("type")} content is not supported by this client]")
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        const val CLIENT_NAME = "md3-agent"
        const val DEFAULT_IMAGE_MIME = "image/png"
        const val MAX_PAGES = 20
        val EMPTY_SCHEMA = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {}
        }
    }
}
