package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextMeterTest {
    private fun row(role: MessageRole, prompt: Int? = null, completion: Int? = null) = MessageEntity(
        id = "m", chatId = "chat", role = role, content = "", reasoning = null, status = MessageStatus.COMPLETE,
        errorText = null, createdAt = 0, modelId = null, toolCallsJson = null, toolCallId = null,
        promptTokens = prompt, completionTokens = completion,
    )

    @Test
    fun `the context is the last round, not the sum of all rounds`() {
        val messages = listOf(
            row(MessageRole.USER),
            row(MessageRole.ASSISTANT, prompt = 1_000, completion = 200),
            row(MessageRole.TOOL),
            row(MessageRole.ASSISTANT, prompt = 1_400, completion = 100),
        )

        assertEquals(1_500, ContextMeter.usedBy(messages))
    }

    @Test
    fun `a failed round without usage falls back to the previous one`() {
        val messages = listOf(row(MessageRole.ASSISTANT, prompt = 900, completion = 50), row(MessageRole.ASSISTANT))

        assertEquals(950, ContextMeter.usedBy(messages))
    }

    @Test
    fun `nothing is known before the first reply finished`() {
        assertNull(ContextMeter.usedBy(listOf(row(MessageRole.USER))))
    }

    @Test
    fun `the limit comes from the model or falls back to the default`() {
        assertEquals(200_000, ContextMeter.limitFor(200_000))
        assertEquals(ContextMeter.DEFAULT_LIMIT, ContextMeter.limitFor(null))
        assertEquals(ContextMeter.DEFAULT_LIMIT, ContextMeter.limitFor(0))
    }

    @Test
    fun `the ring never overflows`() {
        assertEquals(0.5f, ContextMeter(64_000, 128_000).fraction, 0f)
        assertEquals(1f, ContextMeter(150_000, 128_000).fraction, 0f)
    }
}
