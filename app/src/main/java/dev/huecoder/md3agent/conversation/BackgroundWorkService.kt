package dev.huecoder.md3agent.conversation

import android.app.KeyguardManager
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
import dev.huecoder.md3agent.data.settings.ApprovalSurface
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.system.notifications.AppChannel
import dev.huecoder.md3agent.system.notifications.AppNotifications
import dev.huecoder.md3agent.system.notifications.ApprovalNotice
import dev.huecoder.md3agent.system.notifications.NotificationFactory
import dev.huecoder.md3agent.system.notifications.NotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BackgroundWorkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as App).container
    private val factory by lazy { NotificationFactory(this) }
    private var inputPosted = emptyMap<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startInForeground(workNotification(count = 1))
        container.keepAlive.engage(scope)
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
        running = false
        container.keepAlive.disengage()
        notifications().let { manager -> inputPosted.keys.forEach { manager?.cancel(it, NotificationIds.APPROVAL) } }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun render(activity: Map<String, ChatActivity>, appVisible: Boolean) {
        if (activity.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        notifications()?.notify(NotificationIds.WORK, workNotification(activity.size))
        val needed = if (appVisible) emptyMap() else inputNeeded(activity)
        (inputPosted.keys - needed.keys).forEach { notifications()?.cancel(it, NotificationIds.APPROVAL) }
        needed.filter { (chatId, need) -> inputPosted[chatId] != need.key }.forEach { (chatId, need) -> postInput(chatId, need) }
        inputPosted = needed.mapValues { it.value.key }
    }

    private fun inputNeeded(activity: Map<String, ChatActivity>): Map<String, InputNeed> {
        val drafts = container.conversations.drafts.value
        return activity.filterValues { it == ChatActivity.NEEDS_INPUT }.mapValues { (chatId, _) ->
            val draft = drafts[chatId]
            val call = draft?.toolCalls?.firstOrNull { it.id == draft.awaitingCallId }
            if (call != null) InputNeed.Approval(call) else InputNeed.Question
        }
    }

    private suspend fun postInput(chatId: String, need: InputNeed) {
        val title = container.chats.chat(chatId)?.title.orEmpty()
        val notification = when (need) {
            is InputNeed.Approval -> factory.approval(ApprovalNotice(chatId, title, need.call), withButtons = !overlayWillAsk())
            InputNeed.Question -> factory.waiting(chatId, title)
        }
        notifications()?.notify(chatId, NotificationIds.APPROVAL, notification)
    }

    private suspend fun overlayWillAsk(): Boolean {
        if (container.settings.current().notifications.approvals != ApprovalSurface.OVERLAY) return false
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        return container.phoneControl.isReady() && !locked
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NotificationIds.WORK, notification, type)
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

    private fun notifications(): NotificationManager? =
        if (AppNotifications.canPost(this)) getSystemService(NotificationManager::class.java) else null

    private sealed interface InputNeed {
        val key: String

        data class Approval(val call: ToolCall) : InputNeed {
            override val key: String get() = call.id
        }

        data object Question : InputNeed {
            override val key: String = "question"
        }
    }

    companion object {
        private const val TAG = "BackgroundWorkService"
        private const val ACTION_STOP_ALL = "dev.huecoder.md3agent.action.STOP_ALL"
        private const val STOP_REQUEST_CODE = 1
        private const val OPEN_REQUEST_CODE = 2

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
