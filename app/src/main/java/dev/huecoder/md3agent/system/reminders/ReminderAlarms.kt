package dev.huecoder.md3agent.system.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.db.ReminderEntity
import dev.huecoder.md3agent.system.notifications.AppChannel
import dev.huecoder.md3agent.system.notifications.AppNotifications

class ReminderAlarms(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    val canNotify: Boolean get() = AppNotifications.canPost(context)

    fun schedule(reminder: ReminderEntity) {
        val trigger = firePendingIntent(reminder.id)
        if (canScheduleExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, trigger)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, trigger)
        }
    }

    fun cancel(reminderId: String) {
        alarms.cancel(firePendingIntent(reminderId))
        NotificationManagerCompat.from(context).cancel(reminderId, NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    fun notify(reminder: ReminderEntity) {
        if (!canNotify) return
        val notification = NotificationCompat.Builder(context, AppChannel.REMINDERS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(reminder.triggerAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(AppNotifications.openChatIntent(context, reminder.chatId, reminder.id.hashCode()))
            .build()
        NotificationManagerCompat.from(context).notify(reminder.id, NOTIFICATION_ID, notification)
    }

    private fun firePendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .setData("$URI_PREFIX$reminderId".toUri())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val NOTIFICATION_ID = 4_100
        private const val URI_PREFIX = "md3agent://reminder/"

        fun reminderId(intent: Intent): String? = intent.dataString?.removePrefix(URI_PREFIX)?.takeIf { it.isNotEmpty() }
    }
}
