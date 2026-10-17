package dev.huecoder.md3agent.system.health

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dev.huecoder.md3agent.llm.STREAM_LOG_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WorkKeepAlive(context: Context, private val network: NetworkWatch, private val health: DeviceHealth) {
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        .apply { setReferenceCounted(false) }
    private var heartbeat: Job? = null

    fun engage(scope: CoroutineScope) {
        network.start()
        wakeLock.acquire(WAKE_LOCK_HOLD_MS)
        heartbeat = scope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                wakeLock.acquire(WAKE_LOCK_HOLD_MS)
                Log.i(STREAM_LOG_TAG, "working wakeLock=${wakeLock.isHeld} ${health.describe()}")
            }
        }
    }

    fun disengage() {
        heartbeat?.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        network.stop()
    }

    private companion object {
        const val WAKE_LOCK_TAG = "md3agent:background-work"
        const val WAKE_LOCK_HOLD_MS = 10 * 60_000L
        const val HEARTBEAT_MS = 60_000L
    }
}
