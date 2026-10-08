package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ChatTranscriptTest {
    private val messages = listOf(
        message("1", MessageRole.USER, "Привет", 0),
        message("2", MessageRole.TOOL, "{\"raw\": true}", 1_000),
        message("3", MessageRole.ASSISTANT, "Hi there, a long answer", 60_000),
    )

    @Test
    fun `markdown keeps only spoken messages with headings`() {
        val markdown = ChatTranscript.markdown("Greeting", messages, ZoneOffset.UTC)
        assertTrue(markdown.startsWith("# Greeting\n\n## User · 1970-01-01 00:00\n\nПривет\n"))
        assertTrue("## Assistant · 1970-01-01 00:01" in markdown)
        assertFalse("raw" in markdown)
    }

    @Test
    fun `excerpt clips long messages`() {
        val excerpt = ChatTranscript.excerpt(messages, maxCharsPerMessage = 6, zone = ZoneOffset.UTC)
        assertEquals("[1970-01-01 00:00] User: Привет\n\n[1970-01-01 00:01] Assistant: Hi the [...]", excerpt)
    }

    private fun message(id: String, role: MessageRole, content: String, at: Long) = MessageEntity(
        id = id, chatId = "c", role = role, content = content, reasoning = null, status = MessageStatus.COMPLETE,
        errorText = null, createdAt = at, modelId = null, toolCallsJson = null, toolCallId = null,
    )
}
