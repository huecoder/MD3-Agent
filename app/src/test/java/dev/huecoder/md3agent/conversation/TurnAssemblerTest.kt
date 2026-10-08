package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentContent
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.ToolCall
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

    @Test
    fun `stored files are described by path instead of inlined`() {
        val turns = assemble(
            listOf(row(MessageRole.USER, "разбери", attachmentsJson = Attachment.encode(listOf(Attachment("pdf", "doc.pdf", "application/pdf", 2048))))),
            attachments = mapOf("pdf" to AttachmentContent.Stored("doc.pdf", "application/pdf", 2048, "/data/attachments/pdf")),
        )
        assertEquals(
            "разбери\n\nFile \"doc.pdf\" (application/pdf, 2.0 KB) is saved on the device at /data/attachments/pdf. Use the file tools to work with it.",
            turns[1].content,
        )
    }

    @Test
    fun `images from tool results follow the tool turns as one user turn`() {
        val calls = listOf(ToolCall("a", "read_file", "{}"), ToolCall("b", "read_file", "{}"))
        val turns = assemble(
            listOf(
                row(MessageRole.USER, "что на фото?"),
                row(MessageRole.ASSISTANT, calls = calls),
                row(MessageRole.TOOL, "attached", toolCallId = "a", attachmentsJson = Attachment.encode(listOf(Attachment("i1", "a.jpg", "image/jpeg", 1)))),
                row(MessageRole.TOOL, "attached", toolCallId = "b", attachmentsJson = Attachment.encode(listOf(Attachment("i2", "b.jpg", "image/jpeg", 1)))),
                row(MessageRole.ASSISTANT, "кот"),
            ),
            attachments = mapOf(
                "i1" to AttachmentContent.Image("data:1"),
                "i2" to AttachmentContent.Image("data:2"),
            ),
        )
        assertEquals(
            listOf(ChatTurn.Role.SYSTEM, ChatTurn.Role.USER, ChatTurn.Role.ASSISTANT, ChatTurn.Role.TOOL, ChatTurn.Role.TOOL, ChatTurn.Role.USER, ChatTurn.Role.ASSISTANT),
            turns.map { it.role },
        )
        assertEquals(listOf("data:1", "data:2"), turns[5].imageUrls)
    }

    @Test
    fun `files shared with the user are not sent to the model`() {
        val turns = assemble(
            listOf(
                row(MessageRole.USER, "скинь"),
                row(MessageRole.ASSISTANT, calls = listOf(ToolCall("a", "send_files", "{}"))),
                row(MessageRole.TOOL, "sent", toolCallId = "a", attachmentsJson = Attachment.encode(listOf(Attachment("i", "a.jpg", "image/jpeg", 1, forModel = false)))),
            ),
        )
        assertEquals(ChatTurn.Role.TOOL, turns.last().role)
    }
}
