package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.NetworkSettings
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import dev.huecoder.md3agent.net.HttpClientProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NativeChatFormatsTest {
    private val server = MockWebServer()
    private lateinit var client: ChatCompletionClient

    @Before
    fun setUp() {
        server.start()
        client = ChatCompletionClient(HttpClientProvider(flowOf(NetworkSettings()), TestScope()))
    }

    @After
    fun tearDown() = server.close()

    private fun provider(format: ApiFormat) =
        ProviderProfile(id = "p", name = "Test", baseUrl = server.url("/v1").toString(), apiKey = "key-1", format = format)

    private fun sse(vararg events: String) = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream")
        .body(events.joinToString("") { "data: $it\n\n" })
        .build()

    private fun sentBody(): Pair<String, JsonObject> {
        val request = server.takeRequest()
        return request.url.encodedPath to Json.parseToJsonElement(request.body!!.utf8()).jsonObject
    }

    private val toolHistory = listOf(
        ChatTurn(ChatTurn.Role.SYSTEM, "sys"),
        ChatTurn(ChatTurn.Role.USER, "что тут?", imageUrls = listOf("data:image/jpeg;base64,AAA")),
        ChatTurn(ChatTurn.Role.ASSISTANT, "", toolCalls = listOf(ToolCall("c1", "read_screen", """{"full":true}"""))),
        ChatTurn(ChatTurn.Role.TOOL, "текст экрана", toolCallId = "c1"),
        ChatTurn(ChatTurn.Role.USER, "картинки от тула", imageUrls = listOf("data:image/png;base64,BBB"), cacheBreakpoint = true),
    )

    private val tool = ToolSpec("read_screen", "Читает экран", Json.parseToJsonElement("""{"type":"object","properties":{}}""").jsonObject)

    @Test
    fun `anthropic request uses messages api with native blocks and cache`() = runTest {
        server.enqueue(sse("""{"type":"message_stop"}"""))

        client.stream(provider(ApiFormat.ANTHROPIC), "claude-x", toolHistory, listOf(tool)).toList()

        val request = server.takeRequest()
        assertEquals("/v1/messages", request.url.encodedPath)
        assertEquals("key-1", request.headers["x-api-key"])
        assertNull(request.headers["Authorization"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("sys", body["system"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        val image = messages[0]["content"]!!.jsonArray[1].jsonObject["source"]!!.jsonObject
        assertEquals("image/jpeg", image["media_type"]!!.jsonPrimitive.content)
        assertEquals("AAA", image["data"]!!.jsonPrimitive.content)
        val toolUse = messages[1]["content"]!!.jsonArray.single().jsonObject
        assertEquals("true", toolUse["input"]!!.jsonObject["full"]!!.jsonPrimitive.content)
        val followUp = messages[2]["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("tool_result", "text", "image"), followUp.map { it["type"]!!.jsonPrimitive.content })
        assertEquals("c1", followUp[0]["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("ephemeral", followUp.last()["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("read_screen", body["tools"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `anthropic thinking is skipped in the middle of a tool loop`() = runTest {
        server.enqueue(sse("""{"type":"message_stop"}"""))
        server.enqueue(sse("""{"type":"message_stop"}"""))
        val options = CompletionOptions(reasoningEffort = ReasoningEffort.MEDIUM)

        client.stream(provider(ApiFormat.ANTHROPIC), "claude-x", toolHistory.take(2), options = options).toList()
        client.stream(provider(ApiFormat.ANTHROPIC), "claude-x", toolHistory, options = options).toList()

        val fresh = sentBody().second
        assertEquals("8192", fresh["thinking"]!!.jsonObject["budget_tokens"]!!.jsonPrimitive.content)
        assertNull(sentBody().second["thinking"])
    }

    @Test
    fun `anthropic stream maps text, thinking, tool input and usage`() = runTest {
        server.enqueue(
            sse(
                """{"type":"message_start","message":{"usage":{"input_tokens":10,"cache_read_input_tokens":90}}}""",
                """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
                """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}""",
                """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
                """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"При"}}""",
                """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"t1","name":"get_weather","input":{}}}""",
                """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"city\":"}}""",
                """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"\"Москва\"}"}}""",
                """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":42}}""",
                """{"type":"message_stop"}""",
            ),
        )

        val events = client.stream(provider(ApiFormat.ANTHROPIC), "claude-x", toolHistory.take(2)).toList()

        assertEquals(CompletionEvent.ReasoningDelta("hmm"), events[0])
        assertEquals(CompletionEvent.ContentDelta("При"), events[1])
        val accumulator = ToolCallAccumulator()
        events.filterIsInstance<CompletionEvent.ToolCallDelta>().forEach(accumulator::add)
        assertEquals(listOf(ToolCall("t1", "get_weather", """{"city":"Москва"}""")), accumulator.calls())
        assertEquals(CompletionEvent.Usage(TokenUsage(100, 42, 90)), events.filterIsInstance<CompletionEvent.Usage>().single())
        assertEquals(CompletionEvent.Finished("tool_use"), events.last())
    }

    @Test
    fun `gemini request uses streamGenerateContent with native parts`() = runTest {
        server.enqueue(sse("""{"candidates":[{"finishReason":"STOP"}]}"""))

        client.stream(provider(ApiFormat.GEMINI), "models/gemini-2.5-flash", toolHistory, listOf(tool), CompletionOptions(ReasoningEffort.LOW)).toList()

        val request = server.takeRequest()
        assertEquals("/v1/models/gemini-2.5-flash:streamGenerateContent", request.url.encodedPath)
        assertEquals("sse", request.url.queryParameter("alt"))
        assertEquals("key-1", request.headers["x-goog-api-key"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("sys", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val contents = body["contents"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "model", "user"), contents.map { it["role"]!!.jsonPrimitive.content })
        assertEquals("AAA", contents[0]["parts"]!!.jsonArray[1].jsonObject["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        val call = contents[1]["parts"]!!.jsonArray.single().jsonObject
        assertEquals("read_screen", call["functionCall"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val response = contents[2]["parts"]!!.jsonArray[0].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("read_screen", response["name"]!!.jsonPrimitive.content)
        assertEquals("текст экрана", response["response"]!!.jsonObject["result"]!!.jsonPrimitive.content)
        val declaration = body["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject
        assertEquals("object", declaration["parametersJsonSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val thinking = body["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject
        assertEquals("1024", thinking["thinkingBudget"]!!.jsonPrimitive.content)
    }

    @Test
    fun `gemini stream maps thoughts, text, function calls and usage`() = runTest {
        server.enqueue(
            sse(
                """{"candidates":[{"content":{"parts":[{"text":"hmm","thought":true}]}}],"usageMetadata":{"promptTokenCount":5}}""",
                """{"candidates":[{"content":{"parts":[{"text":"Привет"},{"functionCall":{"name":"get_weather","args":{"city":"Москва"}}}]},"finishReason":"STOP"}],""" +
                    """"usageMetadata":{"promptTokenCount":50,"candidatesTokenCount":7,"thoughtsTokenCount":3,"cachedContentTokenCount":20}}""",
            ),
        )

        val events = client.stream(provider(ApiFormat.GEMINI), "gemini-2.5-flash", toolHistory.take(2)).toList()

        assertEquals(CompletionEvent.ReasoningDelta("hmm"), events[0])
        assertEquals(CompletionEvent.ContentDelta("Привет"), events.filterIsInstance<CompletionEvent.ContentDelta>().single())
        val call = events.filterIsInstance<CompletionEvent.ToolCallDelta>().single()
        assertEquals("get_weather", call.name)
        assertEquals("""{"city":"Москва"}""", call.arguments)
        assertEquals(CompletionEvent.Usage(TokenUsage(50, 10, 20)), events.filterIsInstance<CompletionEvent.Usage>().last())
        assertEquals(CompletionEvent.Finished("STOP"), events.last())
    }

    @Test
    fun `gemini 3 models get a thinking level instead of a budget`() = runTest {
        server.enqueue(sse("""{"candidates":[{"finishReason":"STOP"}]}"""))

        client.stream(provider(ApiFormat.GEMINI), "gemini-3-pro-preview", toolHistory.take(2), options = CompletionOptions(ReasoningEffort.HIGH)).toList()

        val thinking = sentBody().second["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject
        assertEquals("high", thinking["thinkingLevel"]!!.jsonPrimitive.content)
        assertNull(thinking["thinkingBudget"])
    }
}
