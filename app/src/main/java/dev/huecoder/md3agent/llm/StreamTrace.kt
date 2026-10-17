package dev.huecoder.md3agent.llm

import android.os.SystemClock
import dev.huecoder.md3agent.net.NetworkFailureKind
import java.io.IOException

const val STREAM_LOG_TAG = "StreamHealth"

data class StreamTimings(val afterMs: Long, val silentMs: Long, val asleepMs: Long, val chunks: Int) {
    fun describe(): String = "afterMs=$afterMs silentMs=$silentMs asleepMs=$asleepMs chunks=$chunks"
}

data class StreamFailure(
    val error: IOException,
    val kind: NetworkFailureKind,
    val timings: StreamTimings,
    val responseStarted: Boolean,
) {
    fun describe(): String =
        "error=${error.javaClass.simpleName} message='${error.message?.take(MESSAGE_LOG_CHARS).orEmpty()}' " +
            "kind=$kind responseStarted=$responseStarted ${timings.describe()}"

    private companion object {
        const val MESSAGE_LOG_CHARS = 120
    }
}

interface StreamDiagnostics {
    fun failed(failure: StreamFailure)

    fun endedWithoutFinish(timings: StreamTimings)
}

object NoStreamDiagnostics : StreamDiagnostics {
    override fun failed(failure: StreamFailure) = Unit

    override fun endedWithoutFinish(timings: StreamTimings) = Unit
}

class StreamTrace(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val uptime: () -> Long = SystemClock::uptimeMillis,
) {
    private val startedAt = elapsedRealtime()
    private val startedUptime = uptime()
    private var lastByteAt = startedAt
    private var chunks = 0

    var responseStarted = false
        private set

    fun onResponse() {
        responseStarted = true
        lastByteAt = elapsedRealtime()
    }

    fun onChunk() {
        chunks += 1
        lastByteAt = elapsedRealtime()
    }

    fun timings(): StreamTimings {
        val now = elapsedRealtime()
        val elapsed = now - startedAt
        return StreamTimings(
            afterMs = elapsed,
            silentMs = now - lastByteAt,
            asleepMs = elapsed - (uptime() - startedUptime),
            chunks = chunks,
        )
    }

    fun failure(error: IOException): StreamFailure =
        StreamFailure(error, NetworkFailureKind.of(error, responseStarted), timings(), responseStarted)
}
