package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.llm.ToolSpec
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBreakdownTest {
    private fun turn(role: ChatTurn.Role, text: String, images: Int = 0, calls: List<ToolCall> = emptyList()) =
        ChatTurn(role, text, imageUrls = List(images) { "data:image/png;base64,AAAA" }, toolCalls = calls)

    private fun tokensOf(breakdown: ContextBreakdown, part: ContextPart) = breakdown.slices.single { it.part == part }.tokens

    @Test
    fun `the slices add up to the reported total`() {
        val turns = listOf(
            turn(ChatTurn.Role.SYSTEM, "x".repeat(4_000)),
            turn(ChatTurn.Role.USER, "x".repeat(400)),
            turn(ChatTurn.Role.ASSISTANT, "x".repeat(800)),
        )

        val breakdown = ContextBreakdown.of(turns, emptyList(), usedTokens = 10_000)

        assertTrue(kotlin.math.abs(10_000 - breakdown.tokens) <= breakdown.slices.size)
    }

    @Test
    fun `the biggest part comes first`() {
        val turns = listOf(turn(ChatTurn.Role.USER, "x".repeat(100)), turn(ChatTurn.Role.SYSTEM, "x".repeat(4_000)))

        val breakdown = ContextBreakdown.of(turns, emptyList(), usedTokens = 1_000)

        assertEquals(ContextPart.INSTRUCTIONS, breakdown.slices.first().part)
    }

    @Test
    fun `tool definitions, calls and images get their own slices`() {
        val tools = listOf(ToolSpec("search", "Looks things up", JsonObject(emptyMap())))
        val turns = listOf(
            turn(ChatTurn.Role.USER, "hi", images = 2),
            turn(ChatTurn.Role.ASSISTANT, "", calls = listOf(ToolCall("1", "search", """{"q":"cats"}"""))),
            turn(ChatTurn.Role.TOOL, "results"),
        )

        val breakdown = ContextBreakdown.of(turns, tools, usedTokens = 5_000)

        assertTrue(breakdown.slices.map { it.part }.containsAll(listOf(ContextPart.TOOL_DEFINITIONS, ContextPart.TOOL_CALLS, ContextPart.IMAGES)))
        assertTrue(tokensOf(breakdown, ContextPart.IMAGES) > tokensOf(breakdown, ContextPart.TOOL_CALLS))
    }

    @Test
    fun `non latin text weighs more per character`() {
        val turns = listOf(turn(ChatTurn.Role.USER, "я".repeat(100)), turn(ChatTurn.Role.ASSISTANT, "a".repeat(100)))

        val breakdown = ContextBreakdown.of(turns, emptyList(), usedTokens = 900)

        assertEquals(600, tokensOf(breakdown, ContextPart.YOUR_MESSAGES))
        assertEquals(300, tokensOf(breakdown, ContextPart.REPLIES))
    }

    @Test
    fun `an empty request has nothing to show`() {
        assertTrue(ContextBreakdown.of(emptyList(), emptyList(), usedTokens = 100).slices.isEmpty())
    }
}
