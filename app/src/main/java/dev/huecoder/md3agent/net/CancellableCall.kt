package dev.huecoder.md3agent.net

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

internal suspend fun <T> Call.executeCancellable(consume: suspend (Response) -> T): T = coroutineScope {
    val call = this@executeCancellable
    val settled = AtomicBoolean(false)
    val abortOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!settled.get()) call.cancel()
        }
    }
    try {
        call.execute().use { consume(it) }
    } finally {
        settled.set(true)
        abortOnCancel.cancel()
    }
}
