package dev.huecoder.md3agent.system.reminders

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.time.DayOfWeek
import java.util.Calendar

class ClockApp(private val context: Context, private val isAppVisible: () -> Boolean) {
    fun setAlarm(hour: Int, minute: Int, label: String?, days: Set<DayOfWeek>): ClockResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        if (days.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days.map(::calendarDay)))
        return launch(intent)
    }

    fun setTimer(seconds: Int, label: String?): ClockResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return launch(intent)
    }

    private fun launch(intent: Intent): ClockResult {
        if (!isAppVisible()) return ClockResult.APP_HIDDEN
        if (intent.resolveActivity(context.packageManager) == null) return ClockResult.NO_CLOCK_APP
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ClockResult.DONE
        } catch (e: ActivityNotFoundException) {
            ClockResult.NO_CLOCK_APP
        } catch (e: SecurityException) {
            ClockResult.BLOCKED
        }
    }

    private fun calendarDay(day: DayOfWeek): Int = when (day) {
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
    }
}

enum class ClockResult { DONE, APP_HIDDEN, NO_CLOCK_APP, BLOCKED }
