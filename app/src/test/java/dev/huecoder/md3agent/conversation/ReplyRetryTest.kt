package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.net.NetworkFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReplyRetryTest {
    private fun http(code: Int, message: String = "HTTP $code") = ProviderException.Http(code, message)

    private fun network(kind: NetworkFailureKind) = ProviderException.Network(IOException("boom"), kind)

    @Test
    fun `a dropped stream is retried up to ten times`() {
        val failure = network(NetworkFailureKind.DROPPED)

        assertNotNull(ReplyRetry.plan(failure, 1))
        assertNotNull(ReplyRetry.plan(failure, 10))
        assertNull(ReplyRetry.plan(failure, 11))
        assertEquals(10, ReplyRetry.plan(failure, 1)?.limit)
    }

    @Test
    fun `unreachable and stalled connections are retried but a bad certificate is not`() {
        assertNotNull(ReplyRetry.plan(network(NetworkFailureKind.UNREACHABLE), 1))
        assertNotNull(ReplyRetry.plan(network(NetworkFailureKind.STALLED), 1))
        assertNull(ReplyRetry.plan(network(NetworkFailureKind.INSECURE), 1))
    }

    @Test
    fun `server errors are retried`() {
        listOf(500, 502, 503, 504, 529).forEach { code -> assertNotNull("HTTP $code", ReplyRetry.plan(http(code), 1)) }
        assertNotNull(ReplyRetry.plan(http(408), 1))
    }

    @Test
    fun `a spent quota or a rejected request stops at once`() {
        listOf(400, 401, 402, 403, 404, 413, 422, 429).forEach { code -> assertNull("HTTP $code", ReplyRetry.plan(http(code), 1)) }
    }

    @Test
    fun `errors reported inside the stream follow their wording`() {
        assertNotNull(ReplyRetry.plan(http(0, "overloaded_error: Overloaded"), 1))
        assertNull(ReplyRetry.plan(http(0, "You exceeded your current quota"), 1))
        assertNull(ReplyRetry.plan(http(0, "Overloaded, rate limit reached"), 1))
        assertNull(ReplyRetry.plan(http(0, "Blocked by Gemini: SAFETY"), 1))
    }

    @Test
    fun `an unreadable response gets only a few tries`() {
        val failure = ProviderException.MalformedResponse("Could not parse response chunk")

        assertNotNull(ReplyRetry.plan(failure, 3))
        assertNull(ReplyRetry.plan(failure, 4))
    }

    @Test
    fun `other failures are never retried`() {
        assertNull(ReplyRetry.plan(ProviderException.InvalidConfiguration("no url"), 1))
        assertNull(ReplyRetry.plan(IllegalStateException("bug"), 1))
    }

    @Test
    fun `the wait grows with every attempt and stops growing at a minute`() {
        val delays = (1..10).map(ReplyRetry::delayMs)

        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L), delays.take(6))
        assertTrue(delays.zipWithNext().all { (before, after) -> after >= before })
        assertEquals(60_000L, delays.last())
    }

    @Test
    fun `attempts outside the range have no plan`() {
        assertNull(ReplyRetry.plan(network(NetworkFailureKind.DROPPED), 0))
    }
}
