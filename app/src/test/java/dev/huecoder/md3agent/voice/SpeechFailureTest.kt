package dev.huecoder.md3agent.voice

import dev.huecoder.md3agent.net.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SpeechFailureTest {
    @Test
    fun `a provider message is pulled out of the error body`() {
        val error = HttpStatusException(401, """{"detail":{"status":"invalid_api_key","message":"Invalid API key"}}""")

        assertEquals("HTTP 401: Invalid API key", SpeechFailure.describe(error))
    }

    @Test
    fun `a body without a message stays as it came`() {
        val error = HttpStatusException(502, "Bad gateway")

        assertEquals("HTTP 502: Bad gateway", SpeechFailure.describe(error))
    }

    @Test
    fun `other failures keep their own text`() {
        assertEquals("Connection reset", SpeechFailure.describe(IOException("Connection reset")))
    }
}
