package dev.huecoder.md3assistant.conversation

import dev.huecoder.md3assistant.data.attachments.Attachment
import dev.huecoder.md3assistant.data.attachments.AttachmentContent
import dev.huecoder.md3assistant.data.db.MessageEntity
import dev.huecoder.md3assistant.data.db.MessageRole
import dev.huecoder.md3assistant.data.db.MessageStatus
import dev.huecoder.md3assistant.llm.ChatTurn
import dev.huecoder.md3assistant.llm.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnAssemblerTest {
    private var clock = 0L

    private fun row(
        role: MessageRole,
        content: String = "",
        status: MessageStatus = MessageStatus.COMPLETE,
        calls: List<ToolCall> = emptyList(),
        toolCallId: String? = null,
        attachmentsJson: String? = null,
    ) = MessageEntity(
        id = "m${++clock}",
        chatId = "c",
        role = role,
        content = content,
        reasoning = null,
        status = status,
        errorText = null,
        createdAt = clock,
        modelId = null,
        toolCallsJson = TurnAssembler.encodeToolCalls(calls),
        toolCallId = toolCallId,
        attachmentsJson = attachmentsJson,
    )

    private fun assemble(history: List<MessageEntity>, attachments: Map<String, AttachmentContent> = emptyMap()) =
        TurnAssembler.assemble("sys", history, attachments)

    @Test
    fun `answered tool calls are kept with their results in order`() {
        val call = ToolCall("call_1", "get_weather", """{"location":"Екатеринбург"}""")
        val turns = assemble(
            listOf(
                row(MessageRole.USER, "погода?"),
                row(MessageRole.ASSISTANT, calls = listOf(call)),
                row(MessageRole.TOOL, "ясно, 12°C", toolCallId = "call_1"),
                row(MessageRole.ASSISTANT, "Ясно и 12 градусов"),
            ),
        )
        assertEquals(
            listOf(ChatTurn.Role.SYSTEM, ChatTurn.Role.USER, ChatTurn.Role.ASSISTANT, ChatTurn.Role.TOOL, ChatTurn.Role.ASSISTANT),
            turns.map { it.role },
        )
        assertEquals(listOf(call), turns[2].toolCalls)
        assertEquals("call_1", turns[3].toolCallId)
    }

    @Test
    fun `unanswered calls are dropped and an empty assistant turn disappears`() {
        val turns = assemble(
            listOf(
                row(MessageRole.USER, "найди"),
                row(MessageRole.ASSISTANT, calls = listOf(ToolCall("call_x", "web_search", "{}")), status = MessageStatus.CANCELLED),
                row(MessageRole.USER, "ладно, забей"),
            ),
        )
        assertEquals(listOf(ChatTurn.Role.SYSTEM, ChatTurn.Role.USER, ChatTurn.Role.USER), turns.map { it.role })
    }

    @Test
    fun `partially answered round keeps only answered calls`() {
        val turns = assemble(
            listOf(
                row(MessageRole.USER, "q"),
                row(MessageRole.ASSISTANT, "смотрю", calls = listOf(ToolCall("a", "x", "{}"), ToolCall("b", "y", "{}"))),
                row(MessageRole.TOOL, "ok", toolCallId = "a"),
            ),
        )
        assertEquals(listOf("a"), turns[2].toolCalls.map { it.id })
        assertEquals(4, turns.size)
    }

    @Test
    fun `failed reply text is not replayed`() {
        val turns = assemble(listOf(row(MessageRole.USER, "q"), row(MessageRole.ASSISTANT, "полуотв", status = MessageStatus.ERROR)))
        assertEquals(2, turns.size)
    }

    @Test
    fun `attachments become image parts and inline file blocks`() {
        val turns = assemble(
            listOf(
                row(
                    MessageRole.USER,
                    "глянь",
                    attachmentsJson = Attachment.encode(
                        listOf(
                            Attachment("img", "photo.jpg", "image/jpeg", 10),
                            Attachment("txt", "notes.md", "text/markdown", 10),
                            Attachment("missing", "gone.txt", "text/plain", 10),
                        ),
                    ),
                ),
            ),
            attachments = mapOf(
                "img" to AttachmentContent.Image("data:image/jpeg;base64,AAA"),
                "txt" to AttachmentContent.Text("notes.md", "# заметки\n"),
            ),
        )
        val user = turns[1]
        assertEquals(listOf("data:image/jpeg;base64,AAA"), user.imageUrls)
        assertTrue(user.content.startsWith("глянь\n\nFile \"notes.md\":\n```\n# заметки\n```"))
    }
}
