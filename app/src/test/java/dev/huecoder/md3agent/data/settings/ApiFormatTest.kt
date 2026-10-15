package dev.huecoder.md3agent.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiFormatTest {
    @Test
    fun `chat providers never offer elevenlabs and voice providers never offer anthropic`() {
        assertEquals(listOf(ApiFormat.OPENAI, ApiFormat.ANTHROPIC, ApiFormat.GEMINI), ApiFormat.CHAT)
        assertEquals(listOf(ApiFormat.OPENAI, ApiFormat.GEMINI, ApiFormat.ELEVENLABS), ApiFormat.VOICE)
    }

    @Test
    fun `the default elevenlabs address is recognised and replaced when the format changes`() {
        assertTrue(ApiFormat.isDefaultBaseUrl("https://api.elevenlabs.io/v1/"))
        assertEquals("https://api.elevenlabs.io/v1", ApiFormat.ELEVENLABS.adoptBaseUrl("https://api.openai.com/v1"))
        assertEquals("https://my.proxy/v1", ApiFormat.ELEVENLABS.adoptBaseUrl("https://my.proxy/v1"))
    }
}
