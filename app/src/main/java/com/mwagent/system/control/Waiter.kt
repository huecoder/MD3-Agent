package com.mwagent.system.control

import kotlinx.coroutines.delay

sealed interface WaitCondition {
    data class TextVisible(val text: String) : WaitCondition
    data class TextGone(val text: String) : WaitCondition
    data class AppInFront(val packageName: String) : WaitCondition
    data class Idle(val quietMs: Long = DEFAULT_QUIET_MS) : WaitCondition
    data object ScreenChanged : WaitCondition

    companion object {
        const val DEFAULT_QUIET_MS = 400L
    }
}

data class WaitOutcome(val met: Boolean, val waitedMs: Long, val detail: String)

class Waiter(
    private val probe: suspend () -> ScreenProbe,
    private val clock: () -> Long,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun await(condition: WaitCondition, timeoutMs: Long): WaitOutcome {
        val startedAt = clock()
        var previous = probe()
        var lastChangeAt = startedAt
        val baseline = previous
        while (true) {
            val now = clock()
            val waited = now - startedAt
            if (previous.fingerprint != baseline.fingerprint && condition == WaitCondition.ScreenChanged) {
                return WaitOutcome(true, waited, "The screen changed after $waited ms")
            }
            satisfied(condition, previous, now - lastChangeAt)?.let { return WaitOutcome(true, waited, it) }
            if (waited >= timeoutMs) return WaitOutcome(false, waited, timeoutNote(condition, timeoutMs))
            pause(minOf(POLL_MS, timeoutMs - waited).coerceAtLeast(1))
            val next = probe()
            if (next.fingerprint != previous.fingerprint) lastChangeAt = clock()
            previous = next
        }
    }

    private fun satisfied(condition: WaitCondition, probe: ScreenProbe, quietForMs: Long): String? = when (condition) {
        is WaitCondition.TextVisible -> "\"${condition.text}\" is on the screen".takeIf { probe.shows(condition.text) }
        is WaitCondition.TextGone -> "\"${condition.text}\" is gone".takeIf { !probe.shows(condition.text) }
        is WaitCondition.AppInFront -> "${condition.packageName} is in front".takeIf { probe.packageName == condition.packageName }
        is WaitCondition.Idle -> "The screen has been still for ${condition.quietMs} ms".takeIf { quietForMs >= condition.quietMs }
        WaitCondition.ScreenChanged -> null
    }

    private fun timeoutNote(condition: WaitCondition, timeoutMs: Long): String = when (condition) {
        is WaitCondition.TextVisible -> "Timed out after $timeoutMs ms: \"${condition.text}\" never appeared"
        is WaitCondition.TextGone -> "Timed out after $timeoutMs ms: \"${condition.text}\" is still on the screen"
        is WaitCondition.AppInFront -> "Timed out after $timeoutMs ms: ${condition.packageName} did not come to the front"
        is WaitCondition.Idle -> "Timed out after $timeoutMs ms: the screen kept changing"
        WaitCondition.ScreenChanged -> "Timed out after $timeoutMs ms: the screen did not change"
    }

    companion object {
        const val POLL_MS = 200L
        const val MIN_TIMEOUT_MS = 100L
        const val MAX_TIMEOUT_MS = 30_000L
    }
}
