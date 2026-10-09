package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTimelineFocusTest {
    private fun message(id: String, role: MessageRole, at: Long, content: String = id) = MessageEntity(
        id = id, chatId = "chat", role = role, content = content, reasoning = null, status = MessageStatus.COMPLETE,
        errorText = null, createdAt = at, modelId = null, toolCallsJson = null, toolCallId = null,
    )

    private val messages = listOf(
        message("ask", MessageRole.USER, 1),
        message("first-round", MessageRole.ASSISTANT, 2),
        message("second-round", MessageRole.ASSISTANT, 3),
        message("follow-up", MessageRole.USER, 4),
        message("answer", MessageRole.ASSISTANT, 5),
    )
    private val items = ChatTimeline.build(messages, draft = null)

    @Test
    fun `user message focuses itself`() {
        assertEquals("follow-up", ChatTimeline.itemFor("follow-up", messages, items))
    }

    @Test
    fun `later round of a reply focuses the whole reply`() {
        assertEquals("first-round", ChatTimeline.itemFor("second-round", messages, items))
    }

    @Test
    fun `unknown message focuses nothing`() {
        assertNull(ChatTimeline.itemFor("gone", messages, items))
    }
}
