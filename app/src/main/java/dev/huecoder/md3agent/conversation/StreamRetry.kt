package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.ProviderException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

internal val SILENT_RETRY_DELAYS_MS = listOf(1_500L, 4_000L, 10_000L)

internal fun Flow<CompletionEvent>.retryWhileSilent(
    delaysMs: List<Long> = SILENT_RETRY_DELAYS_MS,
    onRetry: (attempt: Int, failure: ProviderException.Network, delayMs: Long) -> Unit = { _, _, _ -> },
): Flow<CompletionEvent> = flow {
    var received = false
    emitAll(
        onEach { received = true }.retryWhen { cause, attempt ->
            val failure = cause as? ProviderException.Network
            val delayMs = failure?.takeIf { !received && it.kind.isTransient }?.let { delaysMs.getOrNull(attempt.toInt()) }
            if (failure != null && delayMs != null) {
                onRetry(attempt.toInt() + 1, failure, delayMs)
                delay(delayMs)
            }
            delayMs != null
        },
    )
}
