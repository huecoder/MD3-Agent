package dev.huecoder.md3agent.conversation

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.system.notifications.AppChannel
import dev.huecoder.md3agent.system.notifications.AppNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BackgroundWorkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as App).container
    private var approvalNotified = emptySet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground(workNotification(count = 1))
        scope.launch {
            container.conversations.activity.combine(container.visibility.isVisible) { activity, visible -> activity to visible }
                .collect { (activity, visible) -> render(activity, visible) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) container.conversations.cancelAll()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notifications().let { manager -> approvalNotified.forEach { manager?.cancel(it, APPROVAL_NOTIFICATION_ID) } }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun render(activity: Map<String, ChatActivity>, appVisible: Boolean) {
        if (activity.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        notifications()?.notify(WORK_NOTIFICATION_ID, workNotification(activity.size))
        val waiting = if (appVisible) emptySet() else activity.filterValues { it == ChatActivity.NEEDS_INPUT }.keys
        (approvalNotified - waiting).forEach { notifications()?.cancel(it, APPROVAL_NOTIFICATION_ID) }
        (waiting - approvalNotified).forEach { chatId ->
            val title = container.chats.chat(chatId)?.title.orEmpty()
            notifications()?.notify(chatId, APPROVAL_NOTIFICATION_ID, approvalNotification(chatId, title))
        }
        approvalNotified = waiting
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, WORK_NOTIFICATION_ID, notification, type)
    }

    private fun workNotification(count: Int): Notification {
        val stopAll = PendingIntent.getService(
            this, STOP_REQUEST_CODE,
            Intent(this, BackgroundWorkService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AppChannel.BACKGROUND_WORK.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(resources.getQuantityString(R.plurals.notification_work_title, count, count))
            .setContentText(getString(R.string.notification_work_text))
            .setContentIntent(AppNotifications.openChatIntent(this, chatId = null, OPEN_REQUEST_CODE))
            .addAction(0, getString(R.string.notification_work_stop), stopAll)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun approvalNotification(chatId: String, chatTitle: String): Notification =
        NotificationCompat.Builder(this, AppChannel.APPROVALS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_approval_title))
            .setContentText(chatTitle)
            .setContentIntent(AppNotifications.openChatIntent(this, chatId, chatId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

    private fun notifications(): NotificationManager? =
        if (AppNotifications.canPost(this)) getSystemService(NotificationManager::class.java) else null

    companion object {
        private const val TAG = "BackgroundWorkService"
        private const val ACTION_STOP_ALL = "dev.huecoder.md3agent.action.STOP_ALL"
        private const val WORK_NOTIFICATION_ID = 1001
        private const val APPROVAL_NOTIFICATION_ID = 1002
        private const val STOP_REQUEST_CODE = 1
        private const val OPEN_REQUEST_CODE = 2

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
