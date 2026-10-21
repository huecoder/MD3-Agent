package dev.huecoder.md3agent.system.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.huecoder.md3agent.conversation.LockPolicy
import dev.huecoder.md3agent.conversation.LockRequirement
import dev.huecoder.md3agent.conversation.ToolGate
import dev.huecoder.md3agent.data.settings.LockGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class DeviceLock(
    private val appContext: Context,
    private val mode: () -> LockGuard,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ToolGate {
    private val keyguard = appContext.getSystemService(KeyguardManager::class.java)
    private val prompting = Mutex()

    @Volatile
    private var confirmedUntil = 0L

    @Volatile
    private var answer: CompletableDeferred<Boolean>? = null

    @Volatile
    private var host: DeviceAuthActivity? = null

    init {
        val forgetOnScreenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                confirmedUntil = 0L
            }
        }
        ContextCompat.registerReceiver(appContext, forgetOnScreenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun privateContextAllowed(): Boolean = mode() == LockGuard.OFF || !lockedAndSecure() || confirmed()

    override suspend fun admit(toolName: String): Boolean {
        if (requirement(toolName) == LockRequirement.NONE) return true
        return prompting.withLock {
            when (val needed = requirement(toolName)) {
                LockRequirement.NONE -> true
                else -> ask(needed)
            }
        }
    }

    fun attach(activity: DeviceAuthActivity) {
        host = activity
    }

    fun detach(activity: DeviceAuthActivity) {
        if (host === activity) host = null
    }

    fun report(confirmed: Boolean) {
        answer?.complete(confirmed)
    }

    private fun requirement(toolName: String): LockRequirement = LockPolicy.requirement(mode(), lockedAndSecure(), confirmed(), toolName)

    private fun lockedAndSecure(): Boolean = keyguard.isDeviceSecure && keyguard.isKeyguardLocked

    private fun confirmed(): Boolean = clock() < confirmedUntil

    private suspend fun ask(needed: LockRequirement): Boolean {
        val pending = CompletableDeferred<Boolean>()
        answer = pending
        try {
            val launched = runCatching { appContext.startActivity(DeviceAuthActivity.intent(appContext, unlock = needed == LockRequirement.UNLOCK)) }.isSuccess
            if (!launched) return false
            val granted = withTimeoutOrNull(PROMPT_TIMEOUT_MS) { pending.await() } == true
            if (granted && needed == LockRequirement.CONFIRM) confirmedUntil = clock() + CONFIRMED_FOR_MS
            return granted
        } finally {
            answer = null
            host?.let { activity -> activity.runOnUiThread(activity::finish) }
        }
    }

    private companion object {
        const val PROMPT_TIMEOUT_MS = 120_000L
        const val CONFIRMED_FOR_MS = 5 * 60_000L
    }
}
