package dev.huecoder.md3agent.system.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.huecoder.md3agent.MainActivity
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision

enum class AppChannel(val id: String, @StringRes val title: Int, val importance: Int) {
    BACKGROUND_WORK("background_work", R.string.notification_channel_work, NotificationManager.IMPORTANCE_LOW),
    APPROVALS("approvals", R.string.notification_channel_approvals, NotificationManager.IMPORTANCE_HIGH),
    REMINDERS("reminders", R.string.notification_channel_reminders, NotificationManager.IMPORTANCE_HIGH),
}

object NotificationIds {
    const val WORK = 1001
    const val APPROVAL = 1002
}

object AppNotifications {
    const val TAG = "Notifications"
    const val ACTION_APPROVAL = "dev.huecoder.md3agent.action.APPROVAL"
    const val EXTRA_CHAT_ID = "dev.huecoder.md3agent.extra.NOTIFICATION_CHAT_ID"
    const val EXTRA_CALL_ID = "dev.huecoder.md3agent.extra.NOTIFICATION_CALL_ID"
    const val EXTRA_DECISION = "dev.huecoder.md3agent.extra.NOTIFICATION_DECISION"
    private const val APPROVAL_URI = "md3agent://approval/"

    fun registerChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(AppChannel.entries.map { NotificationChannel(it.id, context.getString(it.title), it.importance) })
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openChatIntent(context: Context, chatId: String?, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, MainActivity.chatIntent(context, chatId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun approvalIntent(context: Context, chatId: String, callId: String, decision: ApprovalDecision): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(ACTION_APPROVAL)
            .setData("$APPROVAL_URI$chatId/$callId/${decision.name}".toUri())
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_CALL_ID, callId)
            .putExtra(EXTRA_DECISION, decision.name)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
