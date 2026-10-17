package com.mwagent.system.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.mwagent.MainActivity
import com.mwagent.R

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

    fun openChatIntent(context: Context, chatId: String?, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, MainActivity.chatIntent(context, chatId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
