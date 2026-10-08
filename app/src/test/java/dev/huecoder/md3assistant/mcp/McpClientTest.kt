package dev.huecoder.md3assistant.mcp

import dev.huecoder.md3assistant.data.settings.McpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpClientTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun client(apiKey: String = "") = McpClient(McpServer("id", "Test", server.url("/mcp").toString(), apiKey = apiKey)) { http }

    private fun json(body: String, sessionId: String? = null) = server.enqueue(
        MockResponse.Builder().body(body).addHeader("Content-Type", "application/json")
            .apply { if (sessionId != null) addHeader("Mcp-Session-Id", sessionId) }.build(),
    )

    private fun accepted() = server.enqueue(MockResponse.Builder().code(202).build())

    private fun initialized(sessionId: String = "s1") {
        json("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{}}}""", sessionId)
        accepted()
    }

    @Test
    fun `initializes once then lists tools from an event stream and keeps the session`() = runTest {
        initialized()
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n" +
                    "data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"search\",\"description\":\"Finds\",\"inputSchema\":{\"type\":\"object\"}}]}}\n\n",
            ).build(),
        )

        val tools = client(apiKey = "k").listTools()

        assertEquals(listOf("search"), tools.map { it.name })
        val initialize = server.takeRequest()
        assertEquals("Bearer k", initialize.headers["Authorization"])
        assertTrue(initialize.body!!.utf8().contains("\"initialize\""))
        val notification = server.takeRequest()
        assertEquals("s1", notification.headers["Mcp-Session-Id"])
        assertEquals("2025-03-26", notification.headers["MCP-Protocol-Version"])
        val list = server.takeRequest()
        assertEquals("s1", list.headers["Mcp-Session-Id"])
    }

    @Test
    fun `tool results keep text and images and report errors`() = runTest {
        initialized()
        json("""{"jsonrpc":"2.0","id":2,"result":{"isError":true,"content":[{"type":"text","text":"bad"},{"type":"image","data":"AAAA","mimeType":"image/jpeg"}]}}""")

        val result = client().callTool("search", buildJsonObject { put("q", "x") })

        assertTrue(result.isError)
        assertEquals(listOf(McpContent.Text("bad"), McpContent.Image("AAAA", "image/jpeg")), result.content)
    }

    @Test
    fun `an expired session is initialized again and the call retried`() = runTest {
        val mcp = client()
        initialized("old")
        json("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}""")
        mcp.listTools()
        server.enqueue(MockResponse.Builder().code(404).build())
        json("""{"jsonrpc":"2.0","id":4,"result":{"protocolVersion":"2025-06-18"}}""", "new")
        accepted()
        json("""{"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"ok"}]}}""")

        val result = mcp.callTool("search", buildJsonObject {})

        assertEquals(listOf(McpContent.Text("ok")), result.content)
        repeat(4) { server.takeRequest() }
        val reinitialize = server.takeRequest()
        assertEquals(null, reinitialize.headers["Mcp-Session-Id"])
    }

    @Test(expected = McpException::class)
    fun `json rpc errors surface as exceptions`() = runTest {
        initialized()
        json("""{"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"Unknown tool"}}""")

        client().callTool("nope", buildJsonObject {})
    }
}
