package com.mwagent.llm

import com.mwagent.data.settings.HeaderEntry
import com.mwagent.data.settings.NetworkSettings
import com.mwagent.data.settings.ProviderProfile
import com.mwagent.net.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ChatCompletionClientTest {
    private val server = MockWebServer()
    private lateinit var client: ChatCompletionClient

    @Before
    fun setUp() {
        server.start()
        client = ChatCompletionClient(HttpClientProvider(flowOf(NetworkSettings()), TestScope()))
    }

    @After
    fun tearDown() = server.close()

    private fun provider(baseUrl: String = server.url("/v1").toString()) = ProviderProfile(
        id = "p",
        name = "Test",
        baseUrl = baseUrl,
        apiKey = "sk-test",
        extraHeaders = listOf(HeaderEntry("X-Org", "org-1")),
    )

    private fun sse(vararg events: String) = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream")
        .body(events.joinToString("") { "data: $it\n\n" })
        .build()

    private val turns = listOf(ChatTurn(ChatTurn.Role.SYSTEM, "sys"), ChatTurn(ChatTurn.Role.USER, "hi"))

    @Test
    fun `streams content and reasoning deltas until DONE`() = runTest {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"reasoning_content":"hmm"}}]}""",
                """{"choices":[{"delta":{"content":"При"}}]}""",
                """{"choices":[{"delta":{"content":"вет"},"finish_reason":"stop"}]}""",
                "[DONE]",
            ),
        )

        val events = client.stream(provider(), "m-1", turns).toList()

        assertEquals(
            listOf(
                CompletionEvent.ReasoningDelta("hmm"),
                CompletionEvent.ContentDelta("При"),
                CompletionEvent.ContentDelta("вет"),
                CompletionEvent.Finished("stop"),
            ),
            events,
        )
    }

    @Test
    fun `reads the usage chunk that trails the finish reason`() = runTest {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}""",
                """{"choices":[],"usage":{"prompt_tokens":1200,"completion_tokens":34,"total_tokens":1234}}""",
                "[DONE]",
            ),
        )

        val events = client.stream(provider(), "m-1", turns).toList()

        assertEquals(CompletionEvent.Usage(TokenUsage(1200, 34)), events.last())
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("true", body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sends model, messages, bearer key and custom headers to chat completions`() = runTest {
        server.enqueue(sse("[DONE]"))

        client.stream(provider(), "m-1", turns).toList()

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.url.encodedPath)
        assertEquals("Bearer sk-test", request.headers["Authorization"])
        assertEquals("org-1", request.headers["X-Org"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("m-1", body["model"]!!.jsonPrimitive.content)
        assertEquals(true, body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(listOf("system", "user"), body["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content })
    }

    @Test
    fun `accepts a base url that already ends with the endpoint path`() = runTest {
        server.enqueue(sse("[DONE]"))

        client.stream(provider(server.url("/v1/chat/completions").toString()), "m", turns).toList()

        assertEquals("/v1/chat/completions", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `stream without finish reason still ends with Finished`() = runTest {
        server.enqueue(sse("""{"choices":[{"delta":{"content":"a"}}]}"""))

        val events = client.stream(provider(), "m", turns).toList()

        assertEquals(CompletionEvent.Finished(null), events.last())
    }

    @Test
    fun `http error surfaces the provider error message`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":{"message":"bad key"}}""").build())

        try {
            client.stream(provider(), "m", turns).toList()
            fail("expected ProviderException.Http")
        } catch (e: ProviderException.Http) {
            assertEquals(401, e.code)
            assertTrue(e.message!!.contains("bad key"))
        }
    }

    @Test
    fun `error event inside the stream fails the flow`() = runTest {
        server.enqueue(sse("""{"error":{"message":"overloaded"}}"""))

        try {
            client.stream(provider(), "m", turns).toList()
            fail("expected ProviderException.Http")
        } catch (e: ProviderException.Http) {
            assertTrue(e.message!!.contains("overloaded"))
        }
    }

    @Test(expected = ProviderException.InvalidConfiguration::class)
    fun `invalid base url is rejected before any request`() = runTest {
        client.stream(provider("not a url"), "m", turns).toList()
    }

    @Test
    fun `streamed tool call fragments are emitted and reassemble into one call`() = runTest {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"get_weather","arguments":""}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"location\":"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"Москва\"}"}}]},"finish_reason":"tool_calls"}]}""",
                "[DONE]",
            ),
        )

        val accumulator = ToolCallAccumulator()
        client.stream(provider(), "m", turns).toList().filterIsInstance<CompletionEvent.ToolCallDelta>().forEach(accumulator::add)

        assertEquals(listOf(ToolCall("call_1", "get_weather", """{"location":"Москва"}""")), accumulator.calls())
    }

    @Test
    fun `tools, tool call history and images are serialized in OpenAI format`() = runTest {
        server.enqueue(sse("[DONE]"))
        val history = listOf(
            ChatTurn(ChatTurn.Role.USER, "что тут?", imageUrls = listOf("data:image/jpeg;base64,AAA")),
            ChatTurn(ChatTurn.Role.ASSISTANT, "", toolCalls = listOf(ToolCall("c1", "read_screen", "{}"))),
            ChatTurn(ChatTurn.Role.TOOL, "текст экрана", toolCallId = "c1"),
        )
        val tool = ToolSpec("read_screen", "Читает экран", Json.parseToJsonElement("""{"type":"object","properties":{}}""").jsonObject)

        client.stream(provider(), "m", history, listOf(tool)).toList()

        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        val userParts = messages[0]["content"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("text", "image_url"), userParts)
        assertEquals(JsonNull, messages[1]["content"])
        assertEquals("c1", messages[1]["tool_calls"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("c1", messages[2]["tool_call_id"]!!.jsonPrimitive.content)
        val function = body["tools"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject
        assertEquals("read_screen", function["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `providers that skip the index and send object arguments still parse`() = runTest {
        server.enqueue(sse("""{"choices":[{"delta":{"tool_calls":[{"id":"a","function":{"name":"x","arguments":{"k":1}}}]},"finish_reason":"tool_calls"}]}"""))

        val delta = client.stream(provider(), "m", turns).toList().filterIsInstance<CompletionEvent.ToolCallDelta>().single()

        assertEquals(CompletionEvent.ToolCallDelta(index = 0, id = "a", name = "x", arguments = """{"k":1}"""), delta)
    }

    @Test
    fun `cancelling the collector aborts a request the server never answers`() = runBlocking {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
        val httpClients = HttpClientProvider(flowOf(NetworkSettings()), TestScope())
        val collecting = launch(Dispatchers.Default) { ChatCompletionClient(httpClients).stream(provider(), "m", turns).collect() }
        server.takeRequest()
        val dispatcher = httpClients.current().dispatcher
        assertEquals(1, dispatcher.runningCallsCount())

        collecting.cancelAndJoin()

        withTimeout(ABORT_TIMEOUT_MS) { while (dispatcher.runningCallsCount() > 0) delay(POLL_MS) }
    }

    private companion object {
        const val ABORT_TIMEOUT_MS = 2_000L
        const val POLL_MS = 10L
    }
}
