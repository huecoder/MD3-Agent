package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.net.NetworkFailureKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.SocketException
import java.net.SocketTimeoutException

class StreamRetryTest {
    private val delays = listOf(1L, 1L)

    private fun dropped() = ProviderException.Network(SocketException("Connection reset"))

    private suspend fun Flow<CompletionEvent>.failureAfterRetries(): ProviderException.Network =
        try {
            retryWhileSilent(delays).toList()
            throw AssertionError("expected the failure to surface")
        } catch (e: ProviderException.Network) {
            e
        }

    @Test
    fun `a drop before any event is retried until the stream answers`() = runTest {
        var attempts = 0
        val stream = flow<CompletionEvent> {
            attempts += 1
            if (attempts < 3) throw dropped()
            emit(CompletionEvent.ContentDelta("ok"))
        }

        val events = stream.retryWhileSilent(delays).toList()

        assertEquals(listOf<CompletionEvent>(CompletionEvent.ContentDelta("ok")), events)
        assertEquals(3, attempts)
    }

    @Test
    fun `a drop after the first event is not retried`() = runTest {
        var attempts = 0
        val stream = flow<CompletionEvent> {
            attempts += 1
            emit(CompletionEvent.ContentDelta("par"))
            throw dropped()
        }

        assertEquals(NetworkFailureKind.DROPPED, stream.failureAfterRetries().kind)
        assertEquals(1, attempts)
    }

    @Test
    fun `retries stop after the last delay`() = runTest {
        var attempts = 0
        val stream = flow<CompletionEvent> {
            attempts += 1
            throw dropped()
        }

        stream.failureAfterRetries()

        assertEquals(delays.size + 1, attempts)
    }

    @Test
    fun `a stalled read is not retried`() = runTest {
        var attempts = 0
        val stream = flow<CompletionEvent> {
            attempts += 1
            throw ProviderException.Network(SocketTimeoutException("timeout"), NetworkFailureKind.STALLED)
        }

        stream.failureAfterRetries()

        assertEquals(1, attempts)
    }

    @Test
    fun `a network error keeps its kind in the stored text`() {
        val stored = ReplyErrors.encode(dropped())

        assertEquals(StoredNetworkError(NetworkFailureKind.DROPPED, "SocketException: Connection reset"), ReplyErrors.decodeNetwork(stored))
    }

    @Test
    fun `other errors are stored as plain text`() {
        val stored = ReplyErrors.encode(ProviderException.Http(401, "HTTP 401: bad key"))

        assertEquals("HTTP 401: bad key", stored)
        assertNull(ReplyErrors.decodeNetwork(stored))
    }
}
