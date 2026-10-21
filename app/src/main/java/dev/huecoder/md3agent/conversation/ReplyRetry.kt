package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.net.NetworkFailureKind

object ReplyRetry {
    data class Plan(val delayMs: Long, val limit: Int)

    const val MAX_ATTEMPTS = 10
    const val MAX_ATTEMPTS_MALFORMED = 3
    private const val FIRST_DELAY_MS = 2_000L
    private const val MAX_DELAY_MS = 60_000L
    private const val MAX_DOUBLINGS = 5
    private const val REQUEST_TIMEOUT = 408
    private const val TOO_EARLY = 425
    private const val IN_STREAM_ERROR = 0
    private val SERVER_ERRORS = 500..599
    private val TRANSIENT_MARKERS = listOf("overloaded", "internal server error", "temporarily", "try again", "unavailable", "timeout", "timed out", "capacity")
    private val QUOTA_MARKERS = listOf("quota", "billing", "credit", "insufficient", "rate limit", "too many requests")

    fun plan(failure: Throwable, attempt: Int): Plan? {
        val limit = limitFor(failure)
        if (limit == 0 || attempt !in 1..limit) return null
        return Plan(delayMs(attempt), limit)
    }

    fun delayMs(attempt: Int): Long =
        (FIRST_DELAY_MS shl (attempt - 1).coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(MAX_DELAY_MS)

    private fun limitFor(failure: Throwable): Int = when (failure) {
        is ProviderException.Network -> if (failure.kind == NetworkFailureKind.INSECURE) 0 else MAX_ATTEMPTS
        is ProviderException.Http -> if (isTransient(failure)) MAX_ATTEMPTS else 0
        is ProviderException.MalformedResponse -> MAX_ATTEMPTS_MALFORMED
        else -> 0
    }

    private fun isTransient(failure: ProviderException.Http): Boolean = when (failure.code) {
        IN_STREAM_ERROR -> failure.message.orEmpty().let { text ->
            QUOTA_MARKERS.none { text.contains(it, ignoreCase = true) } && TRANSIENT_MARKERS.any { text.contains(it, ignoreCase = true) }
        }
        REQUEST_TIMEOUT, TOO_EARLY -> true
        else -> failure.code in SERVER_ERRORS
    }
}
