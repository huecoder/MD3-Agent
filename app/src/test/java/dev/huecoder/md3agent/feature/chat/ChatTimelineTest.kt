package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.conversation.StreamingDraft
import dev.huecoder.md3agent.conversation.TurnAssembler
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.tools.ShowUiTool
import dev.huecoder.md3agent.tools.web.SearchHit
import dev.huecoder.md3agent.tools.web.SearchResults
import dev.huecoder.md3agent.tools.web.WebSearchTool
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineTest {
    private var clock = 0L

    private fun row(
        role: MessageRole,
        content: String = "",
        calls: List<ToolCall> = emptyList(),
        toolCallId: String? = null,
        status: MessageStatus = MessageStatus.COMPLETE,
    ) = MessageEntity(
        id = "m${++clock}", chatId = "c", role = role, content = content, reasoning = null, status = status,
        errorText = null, createdAt = clock, modelId = null,
        toolCallsJson = TurnAssembler.encodeToolCalls(calls), toolCallId = toolCallId,
    )

    private val search = ToolCall("s1", WebSearchTool.NAME, """{"query":"погода api"}""")
    private val ui = ToolCall("u1", ShowUiTool.NAME, """{"suggestions":["Ещё"]}""")
    private val results = Json.encodeToString(
        SearchResults.serializer(),
        SearchResults("погода api", listOf(SearchHit("Open-Meteo", "https://www.open-meteo.com/docs"))),
    )

    @Test
    fun `a multi-round reply is one item ending with sources and suggestions, with show_ui hidden`() {
        val messages = listOf(
            row(MessageRole.USER, "найди"),
            row(MessageRole.ASSISTANT, calls = listOf(search)),
            row(MessageRole.TOOL, results, toolCallId = "s1"),
            row(MessageRole.ASSISTANT, "Вот", calls = listOf(ui)),
            row(MessageRole.TOOL, "ok", toolCallId = "u1"),
        )

        val timeline = ChatTimeline.build(messages, draft = null)

        assertEquals(2, timeline.size)
        val reply = timeline[1] as ChatItem.Reply
        assertEquals(messages[1].id, reply.id)
        val (tools, text) = reply.parts
        assertEquals(ToolStatus.DONE, (tools as ReplyPart.Work).calls.single().status)
        assertEquals("Вот", (text as ReplyPart.Text).content)
        assertEquals("show_ui is rendered as UI, not as a tool step", 2, reply.parts.size)
        val end = reply.end!!
        assertEquals(listOf("open-meteo.com"), end.sources.map { it.host })
        assertEquals(listOf("Ещё"), end.ui.suggestions)
        assertTrue(end.isLatest)
    }

    @Test
    fun `tool rounds without text in between fold into one group`() {
        val read = ToolCall("r1", "read_webpage", """{"url":"https://a.b"}""")
        val messages = listOf(
            row(MessageRole.USER, "найди"),
            row(MessageRole.ASSISTANT, calls = listOf(search)),
            row(MessageRole.TOOL, results, toolCallId = "s1"),
            row(MessageRole.ASSISTANT, calls = listOf(read)),
            row(MessageRole.TOOL, "page", toolCallId = "r1"),
            row(MessageRole.ASSISTANT, "Готово"),
        )

        val reply = ChatTimeline.build(messages, draft = null)[1] as ChatItem.Reply

        assertEquals(listOf("s1", "r1"), (reply.parts[0] as ReplyPart.Work).calls.map { it.id })
        assertEquals(ReplyPart.Text("Готово", isStreaming = false), reply.parts[1])
    }

    @Test
    fun `reasoning between tool rounds stays in the same work group`() {
        val read = ToolCall("r1", "read_webpage", """{"url":"https://a.b"}""")
        val messages = listOf(
            row(MessageRole.USER, "найди"),
            row(MessageRole.ASSISTANT, calls = listOf(search)).copy(reasoning = "ищу"),
            row(MessageRole.TOOL, results, toolCallId = "s1"),
            row(MessageRole.ASSISTANT, calls = listOf(read)).copy(reasoning = "читаю"),
            row(MessageRole.TOOL, "page", toolCallId = "r1"),
            row(MessageRole.ASSISTANT, "Готово").copy(reasoning = "итог"),
        )

        val reply = ChatTimeline.build(messages, draft = null)[1] as ChatItem.Reply

        assertEquals(2, reply.parts.size)
        val work = reply.parts[0] as ReplyPart.Work
        assertEquals(listOf("ищу", null, "читаю", null, "итог"), work.steps.map { (it as? WorkStep.Thought)?.text })
        assertEquals(listOf("s1", "r1"), work.calls.map { it.id })
        assertEquals(ReplyPart.Text("Готово", isStreaming = false), reply.parts[1])
    }

    @Test
    fun `calls of the round being worked on are running, and the reply has no end yet`() {
        val messages = listOf(row(MessageRole.USER, "найди"), row(MessageRole.ASSISTANT, calls = listOf(search)))
        val draft = StreamingDraft(messages[1].id, "", "", listOf(search), runningCallIds = setOf("s1"), awaitingCallId = null, usage = null)

        val reply = ChatTimeline.build(messages, draft)[1] as ChatItem.Reply

        assertEquals(ToolStatus.RUNNING, (reply.parts.single() as ReplyPart.Work).calls.single().status)
        assertTrue(reply.isActive)
        assertFalse(reply.isWaiting)
        assertNull(reply.end)
        assertNull(reply.finishedAt)
    }

    @Test
    fun `waiting shows only before the first part of the reply`() {
        val first = row(MessageRole.USER, "найди")
        val emptyRound = row(MessageRole.ASSISTANT)
        val emptyDraft = StreamingDraft(emptyRound.id, "", "", emptyList(), runningCallIds = emptySet(), awaitingCallId = null, usage = null)
        assertTrue((ChatTimeline.build(listOf(first, emptyRound), emptyDraft)[1] as ChatItem.Reply).isWaiting)

        val workedRound = row(MessageRole.ASSISTANT, calls = listOf(search))
        val result = row(MessageRole.TOOL, "найдено").copy(toolCallId = "s1")
        val nextRound = row(MessageRole.ASSISTANT)
        val nextDraft = StreamingDraft(nextRound.id, "", "", emptyList(), runningCallIds = emptySet(), awaitingCallId = null, usage = null)

        val reply = ChatTimeline.build(listOf(first, workedRound, result, nextRound), nextDraft)[1] as ChatItem.Reply

        assertTrue(reply.isActive)
        assertFalse(reply.isWaiting)
    }

    @Test
    fun `usage is summed over rounds and duration spans the whole reply`() {
        val messages = listOf(
            row(MessageRole.USER, "найди"),
            row(MessageRole.ASSISTANT, calls = listOf(search)).copy(finishedAt = 10, promptTokens = 100, completionTokens = 5),
            row(MessageRole.TOOL, results, toolCallId = "s1"),
            row(MessageRole.ASSISTANT, "Вот").copy(finishedAt = 50, promptTokens = 300, completionTokens = 40),
        )

        val reply = ChatTimeline.build(messages, draft = null)[1] as ChatItem.Reply

        assertEquals(TokenUsage(400, 45), reply.usage)
        assertEquals(messages[1].createdAt, reply.startedAt)
        assertEquals(50L, reply.finishedAt)
    }

    @Test
    fun `calls left without results after a stop are cancelled, and older replies lose suggestions`() {
        val messages = listOf(
            row(MessageRole.USER, "1"),
            row(MessageRole.ASSISTANT, "a", calls = listOf(ui)),
            row(MessageRole.TOOL, "ok", toolCallId = "u1"),
            row(MessageRole.USER, "2"),
            row(MessageRole.ASSISTANT, calls = listOf(search), status = MessageStatus.CANCELLED),
        )

        val timeline = ChatTimeline.build(messages, draft = null)

        assertFalse((timeline[1] as ChatItem.Reply).end!!.isLatest)
        val stopped = timeline[3] as ChatItem.Reply
        assertEquals(ToolStatus.CANCELLED, (stopped.parts.single() as ReplyPart.Work).calls.single().status)
    }

    @Test
    fun `text of a stopped round is marked stopped, also while its draft is still being removed`() {
        val messages = listOf(row(MessageRole.USER, "1"), row(MessageRole.ASSISTANT, "Привет, мир", status = MessageStatus.CANCELLED))
        val draft = StreamingDraft(messages[1].id, "Привет, мир", "", emptyList(), runningCallIds = emptySet(), awaitingCallId = null, usage = null)

        val settled = ChatTimeline.build(messages, draft = null)[1] as ChatItem.Reply
        val lingering = ChatTimeline.build(messages, draft)[1] as ChatItem.Reply

        assertEquals(ReplyPart.Text("Привет, мир", isStreaming = false, isStopped = true), settled.parts.single())
        assertEquals(ReplyPart.Text("Привет, мир", isStreaming = true, isStopped = true), lingering.parts.single())
    }
}
