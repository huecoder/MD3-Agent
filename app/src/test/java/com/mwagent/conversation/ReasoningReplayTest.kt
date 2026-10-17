package com.mwagent.conversation

import com.mwagent.data.db.MessageEntity
import com.mwagent.data.db.MessageRole
import com.mwagent.data.db.MessageStatus
import com.mwagent.data.settings.ApiFormat
import com.mwagent.llm.ReasoningBlock
import com.mwagent.llm.ReasoningTrace
import com.mwagent.llm.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningReplayTest {
    private var clock = 0L
    private val trace = ReasoningTrace(
        ApiFormat.ANTHROPIC,
        "claude-x",
        "prompt",
        listOf(ReasoningBlock(Json.parseToJsonElement("""{"type":"thinking","thinking":"plan","signature":"SIG"}""").jsonObject)),
    )

    private fun row(role: MessageRole, content: String = "", calls: List<ToolCall> = emptyList(), toolCallId: String? = null, stored: ReasoningTrace? = null) = MessageEntity(
        id = "m${++clock}",
        chatId = "c",
        role = role,
        content = content,
        reasoning = null,
        status = MessageStatus.COMPLETE,
        errorText = null,
        createdAt = clock,
        modelId = null,
        toolCallsJson = TurnAssembler.encodeToolCalls(calls),
        toolCallId = toolCallId,
        reasoningTraceJson = stored?.let(TurnAssembler::encodeTrace),
    )

    private fun call(id: String) = ToolCall(id, "read_screen", "{}")

    @Test
    fun `a stored trace survives the database round trip`() {
        assertEquals(trace, TurnAssembler.decodeTrace(TurnAssembler.encodeTrace(trace)))
        assertNull(TurnAssembler.decodeTrace(null))
        assertNull(TurnAssembler.decodeTrace("not json"))
    }

    @Test
    fun `only the tool rounds after the last user message carry their trace`() {
        val turns = TurnAssembler.assemble(
            "sys",
            listOf(
                row(MessageRole.USER, "first"),
                row(MessageRole.ASSISTANT, calls = listOf(call("old")), stored = trace),
                row(MessageRole.TOOL, "result", toolCallId = "old"),
                row(MessageRole.ASSISTANT, "answer"),
                row(MessageRole.USER, "second"),
                row(MessageRole.ASSISTANT, calls = listOf(call("new")), stored = trace),
                row(MessageRole.TOOL, "result", toolCallId = "new"),
            ),
            emptyMap(),
        )

        val assistants = turns.filter { it.role == com.mwagent.llm.ChatTurn.Role.ASSISTANT }
        assertNull(assistants[0].reasoning)
        assertNull(assistants[1].reasoning)
        assertEquals(trace, assistants[2].reasoning)
    }

    @Test
    fun `a round whose calls were never answered drops its trace with the calls`() {
        val turns = TurnAssembler.assemble(
            "sys",
            listOf(row(MessageRole.USER, "q"), row(MessageRole.ASSISTANT, "text", calls = listOf(call("lost")), stored = trace)),
            emptyMap(),
        )

        assertNull(turns.last().reasoning)
    }
}
