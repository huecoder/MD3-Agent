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
import dev.huecoder.md3agent.MainActivity
import dev.huecoder.md3agent.R

enum class AppChannel(val id: String, @StringRes val title: Int, val importance: Int) {
    BACKGROUND_WORK("background_work", R.string.notification_channel_work, NotificationManager.IMPORTANCE_LOW),
    APPROVALS("approvals", R.string.notification_channel_approvals, NotificationManager.IMPORTANCE_HIGH),
    REMINDERS("reminders", R.string.notification_channel_reminders, NotificationManager.IMPORTANCE_HIGH),
}

object AppNotifications {
    fun registerChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(AppChannel.entries.map { NotificationChannel(it.id, context.getString(it.title), it.importance) })
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openChatIntent(context: Context, chatId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { chatId?.let { putExtra(MainActivity.EXTRA_CHAT_ID, it) } }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
