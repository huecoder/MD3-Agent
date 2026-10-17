package com.mwagent.system.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mwagent.App
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as App).container
        val reminders = container.reminders
        val work: suspend () -> Unit = when (intent.action) {
            ACTION_FIRE -> ReminderAlarms.reminderId(intent)?.let { id -> { reminders.fire(id) } } ?: return
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> reminders::restore
            else -> return
        }
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                work()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.mwagent.action.FIRE_REMINDER"
    }
}
