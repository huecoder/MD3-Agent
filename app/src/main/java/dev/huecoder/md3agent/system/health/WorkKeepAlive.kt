package dev.huecoder.md3agent.system.health

import android.util.Log
import dev.huecoder.md3agent.llm.STREAM_LOG_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WorkKeepAlive(private val network: NetworkWatch, private val health: DeviceHealth) {
    private var heartbeat: Job? = null

    fun engage(scope: CoroutineScope) {
        network.start()
        heartbeat = scope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                Log.i(STREAM_LOG_TAG, "working ${health.describe()}")
            }
        }
    }

    fun disengage() {
        heartbeat?.cancel()
        network.stop()
    }

    private companion object {
        const val HEARTBEAT_MS = 60_000L
    }
}
