package dev.huecoder.md3agent.conversation

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.system.notifications.NotificationIds
import dev.huecoder.md3agent.system.notifications.WorkNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class BackgroundWorkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as App).container
    private lateinit var notifier: WorkNotifier
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        notifier = WorkNotifier(this, container, onIdle = ::finish)
        startInForeground(notifier.initial())
        container.keepAlive.engage(scope)
        notifier.run(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_STOP_ALL) container.conversations.cancelAll()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        container.keepAlive.disengage()
        notifier.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun finish() {
        if (!stopSelfResult(lastStartId)) return
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NotificationIds.WORK, notification, type)
    }

    companion object {
        private const val TAG = "BackgroundWorkService"
        const val ACTION_STOP_ALL = "dev.huecoder.md3agent.action.STOP_ALL"

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, BackgroundWorkService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not keep the work alive in the background", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Foreground service is not allowed", e)
            }
        }
    }
}
