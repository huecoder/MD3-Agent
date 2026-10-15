package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import dev.huecoder.md3agent.llm.wire.AnthropicChatWire
import dev.huecoder.md3agent.llm.wire.ChatRequest
import dev.huecoder.md3agent.llm.wire.ChatWire
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningSignaturesTest {
    private val model = "claude-x"
    private val tool = ToolSpec("read_screen", "Reads the screen", Json.parseToJsonElement("""{"type":"object","properties":{}}""").jsonObject)
    private val call = ToolCall("t1", "read_screen", "{}")
    private val thinking = block("""{"type":"thinking","thinking":"plan","signature":"SIG-A"}""")
    private val progress = block("""{"type":"thinking","thinking":"","signature":"SIG-B"}""", anchor = "t1")

    private fun block(raw: String, anchor: String? = null) = ReasoningBlock(Json.parseToJsonElement(raw).jsonObject, anchor)

    private fun events(wire: ChatWire, vararg chunks: String): List<CompletionEvent> {
        val stream = wire.newStream()
        return chunks.flatMap { stream.events(Json.parseToJsonElement(it).jsonObject) }
    }

    private fun bodyOf(request: Request): JsonObject = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject

    private fun endpoint(format: ApiFormat) = ProviderEndpoint(ProviderProfile("p", "Test", "https://example.test/v1", apiKey = "k", format = format))

    private fun loop(trace: (String) -> ReasoningTrace?): List<ChatTurn> {
        val base = listOf(
            ChatTurn(ChatTurn.Role.SYSTEM, "sys"),
            ChatTurn(ChatTurn.Role.USER, "what is on screen?"),
        )
        val fingerprint = promptFingerprint(base, listOf(tool))
        return base + listOf(
            ChatTurn(ChatTurn.Role.ASSISTANT, "looking", toolCalls = listOf(call), reasoning = trace(fingerprint)),
            ChatTurn(ChatTurn.Role.TOOL, "screen text", toolCallId = "t1"),
        )
    }

    private fun trace(format: ApiFormat, model: String, prompt: String, vararg blocks: ReasoningBlock) = ReasoningTrace(format, model, prompt, blocks.toList())

    private fun anthropicBody(turns: List<ChatTurn>, effort: ReasoningEffort? = ReasoningEffort.MEDIUM) =
        bodyOf(AnthropicChatWire.request(endpoint(ApiFormat.ANTHROPIC), ChatRequest(model, turns, listOf(tool), CompletionOptions(reasoningEffort = effort))))

    private fun assistantTypes(body: JsonObject) =
        body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }

    @Test
    fun `anthropic stream collects thinking, redacted and progress blocks with their signatures`() {
        val events = events(
            AnthropicChatWire,
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"pla"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"n"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"SIG-A"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"redacted_thinking","data":"ENC"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"content_block_start","index":2,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"looking"}}""",
            """{"type":"content_block_stop","index":2}""",
            """{"type":"content_block_start","index":3,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            """{"type":"content_block_delta","index":3,"delta":{"type":"thinking_delta","thinking":""}}""",
            """{"type":"content_block_delta","index":3,"delta":{"type":"signature_delta","signature":"SIG-B"}}""",
            """{"type":"content_block_stop","index":3}""",
            """{"type":"content_block_start","index":4,"content_block":{"type":"tool_use","id":"t1","name":"read_screen","input":{}}}""",
        )

        val blocks = events.filterIsInstance<CompletionEvent.ReasoningBlockDone>().map { it.block }
        assertEquals(
            listOf(
                block("""{"type":"thinking","thinking":"plan","signature":"SIG-A"}"""),
                block("""{"type":"redacted_thinking","data":"ENC"}"""),
                block("""{"type":"thinking","thinking":"","signature":"SIG-B"}""", anchor = "t1"),
            ),
            blocks,
        )
        val progressIndex = events.indexOfFirst { it is CompletionEvent.ReasoningBlockDone && it.block.anchor == "t1" }
        assertTrue(events[progressIndex + 1] is CompletionEvent.ToolCallDelta)
    }

    @Test
    fun `anthropic replays the stored blocks in their original order and keeps thinking on`() {
        val turns = loop { trace(ApiFormat.ANTHROPIC, model, it, thinking, progress) }

        val body = anthropicBody(turns)

        assertEquals(listOf("thinking", "text", "thinking", "tool_use"), assistantTypes(body))
        val content = body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals("SIG-A", content[0].jsonObject["signature"]!!.jsonPrimitive.content)
        assertEquals("SIG-B", content[2].jsonObject["signature"]!!.jsonPrimitive.content)
        assertEquals("enabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `anthropic leaves a loop alone when nothing was stored`() {
        val body = anthropicBody(loop { null })

        assertEquals(listOf("text", "tool_use"), assistantTypes(body))
        assertNull(body["thinking"])
    }

    @Test
    fun `anthropic drops the blocks when the prompt changed since they were made`() {
        val body = anthropicBody(loop { trace(ApiFormat.ANTHROPIC, model, "another prompt", thinking) })

        assertEquals(listOf("text", "tool_use"), assistantTypes(body))
        assertNull(body["thinking"])
    }

    @Test
    fun `anthropic drops the blocks made by another model or another format`() {
        assertEquals(listOf("text", "tool_use"), assistantTypes(anthropicBody(loop { trace(ApiFormat.ANTHROPIC, "claude-other", it, thinking) })))
        assertEquals(listOf("text", "tool_use"), assistantTypes(anthropicBody(loop { trace(ApiFormat.OPENAI, model, it, thinking) })))
    }

    @Test
    fun `anthropic without an effort still replays what the model produced on its own`() {
        val body = anthropicBody(loop { trace(ApiFormat.ANTHROPIC, model, it, thinking) }, effort = null)

        assertEquals(listOf("thinking", "text", "tool_use"), assistantTypes(body))
        assertNull(body["thinking"])
    }

    @Test
    fun `openai stream passes reasoning details through and the request sends them back`() {
        val detail = """{"type":"reasoning.text","text":"plan","signature":"SIG","format":"anthropic-claude-v1","index":0}"""
        val events = events(ChatWire.of(ApiFormat.OPENAI), """{"choices":[{"delta":{"reasoning_details":[$detail]}}]}""")
        val blocks = events.filterIsInstance<CompletionEvent.ReasoningBlockDone>().map { it.block }
        assertEquals(listOf(block(detail)), blocks)

        val turns = loop { trace(ApiFormat.OPENAI, model, it, blocks.single()) }
        val body = bodyOf(ChatWire.of(ApiFormat.OPENAI).request(endpoint(ApiFormat.OPENAI), ChatRequest(model, turns, listOf(tool), CompletionOptions())))
        val assistant = body["messages"]!!.jsonArray[2].jsonObject
        assertEquals("SIG", assistant["reasoning_details"]!!.jsonArray.single().jsonObject["signature"]!!.jsonPrimitive.content)
    }

    @Test
    fun `openai does not send details that came from another prompt`() {
        val turns = loop { trace(ApiFormat.OPENAI, model, "another prompt", block("""{"type":"reasoning.text","text":"x"}""")) }
        val body = bodyOf(ChatWire.of(ApiFormat.OPENAI).request(endpoint(ApiFormat.OPENAI), ChatRequest(model, turns, listOf(tool), CompletionOptions())))
        assertNull(body["messages"]!!.jsonArray[2].jsonObject["reasoning_details"])
    }

    @Test
    fun `gemini keeps the signature of a function call and replays it`() {
        val events = events(
            ChatWire.of(ApiFormat.GEMINI),
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"read_screen","args":{}},"thoughtSignature":"REAL-SIG"}]}}]}""",
        )
        val delta = events.filterIsInstance<CompletionEvent.ToolCallDelta>().single()
        assertEquals("REAL-SIG", delta.signature)

        val accumulator = ToolCallAccumulator()
        accumulator.add(delta)
        val kept = accumulator.calls().single()
        assertEquals("REAL-SIG", kept.signature)

        val turns = listOf(
            ChatTurn(ChatTurn.Role.SYSTEM, "sys"),
            ChatTurn(ChatTurn.Role.USER, "hi"),
            ChatTurn(ChatTurn.Role.ASSISTANT, "", toolCalls = listOf(kept, ToolCall("t2", "read_screen", "{}"))),
            ChatTurn(ChatTurn.Role.TOOL, "a", toolCallId = kept.id),
            ChatTurn(ChatTurn.Role.TOOL, "b", toolCallId = "t2"),
        )
        val body = bodyOf(ChatWire.of(ApiFormat.GEMINI).request(endpoint(ApiFormat.GEMINI), ChatRequest("gemini-3-pro", turns, listOf(tool), CompletionOptions())))
        val parts = body["contents"]!!.jsonArray[1].jsonObject["parts"]!!.jsonArray.map { it.jsonObject }
        assertEquals("REAL-SIG", parts[0]["thoughtSignature"]!!.jsonPrimitive.content)
        assertEquals("skip_thought_signature_validator", parts[1]["thoughtSignature"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a plain tool call carries no signature field when stored`() {
        val encoded = Json.encodeToString(ToolCall.serializer(), ToolCall("a", "b", "{}"))
        assertFalse(encoded.contains("signature"))
        assertEquals("S", Json.decodeFromString(ToolCall.serializer(), Json.encodeToString(ToolCall.serializer(), ToolCall("a", "b", "{}", "S"))).signature)
    }

    @Test
    fun `the fingerprint follows the system prompt and the tools only`() {
        val turns = listOf(ChatTurn(ChatTurn.Role.SYSTEM, "sys"), ChatTurn(ChatTurn.Role.USER, "a"))
        val same = turns + ChatTurn(ChatTurn.Role.ASSISTANT, "more")
        assertEquals(promptFingerprint(turns, listOf(tool)), promptFingerprint(same, listOf(tool)))
        assertFalse(promptFingerprint(turns, listOf(tool)) == promptFingerprint(turns, emptyList()))
        assertFalse(promptFingerprint(turns, listOf(tool)) == promptFingerprint(listOf(ChatTurn(ChatTurn.Role.SYSTEM, "other")), listOf(tool)))
    }

    @Test
    fun `only a bad request that mentions thinking asks for a retry without the replay`() {
        assertTrue(ProviderException.Http(400, "HTTP 400: Invalid `signature` in `thinking` block").rejectsReplayedReasoning())
        assertFalse(ProviderException.Http(400, "HTTP 400: max_tokens is too large").rejectsReplayedReasoning())
        assertFalse(ProviderException.Http(500, "HTTP 500: thinking failed").rejectsReplayedReasoning())
    }

    @Test
    fun `stripping the replay clears it from every turn`() {
        val turns = loop { trace(ApiFormat.ANTHROPIC, model, it, thinking) }
        assertTrue(turns.carriesReasoning())
        assertFalse(turns.withoutReasoning().carriesReasoning())
        assertEquals(turns.size, turns.withoutReasoning().size)
    }
}
