package dev.huecoder.md3agent.system.health

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import dev.huecoder.md3agent.llm.STREAM_LOG_TAG
import dev.huecoder.md3agent.llm.StreamDiagnostics
import dev.huecoder.md3agent.llm.StreamFailure
import dev.huecoder.md3agent.llm.StreamTimings

class DeviceHealth(
    private val context: Context,
    private val network: NetworkWatch,
    private val appVisible: () -> Boolean,
    private val workerAlive: () -> Boolean,
) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val usage = context.getSystemService(UsageStatsManager::class.java)

    fun isUnrestricted(): Boolean = power.isIgnoringBatteryOptimizations(context.packageName)

    fun describe(): String = listOf(
        "unrestricted=${isUnrestricted()}",
        "idle=${power.isDeviceIdleMode}",
        "powerSave=${power.isPowerSaveMode}",
        "bucket=${bucketName(usage.appStandbyBucket)}",
        "worker=${workerAlive()}",
        "visible=${appVisible()}",
        network.summary(),
    ).joinToString(" ")

    fun streamDiagnostics(): StreamDiagnostics = object : StreamDiagnostics {
        override fun failed(failure: StreamFailure) {
            Log.w(STREAM_LOG_TAG, "stream failed ${failure.describe()} ${describe()}")
        }

        override fun endedWithoutFinish(timings: StreamTimings) {
            Log.w(STREAM_LOG_TAG, "stream ended without a finish reason ${timings.describe()} ${describe()}")
        }
    }

    private fun bucketName(bucket: Int): String = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
        else -> bucket.toString()
    }
}
