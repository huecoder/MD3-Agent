package com.mwagent.tools.reminders

import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.reminders.ClockApp
import com.mwagent.system.reminders.ClockResult
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalArray
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.stringArrayProperty
import com.mwagent.tools.stringProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek

private fun ClockResult.toOutcome(done: String): ToolOutcome = when (this) {
    ClockResult.DONE -> ToolOutcome(done)
    ClockResult.APP_HIDDEN -> ToolOutcome.error("The app is in the background, Android only lets the clock open while the user has the app on screen")
    ClockResult.NO_CLOCK_APP -> ToolOutcome.error("No clock app on this device accepts alarms and timers")
    ClockResult.BLOCKED -> ToolOutcome.error("The clock app refused the request")
}

private fun JsonObject.requireInt(name: String, range: IntRange): Int {
    val value = optionalInt(name) ?: throw ToolArgumentException("An integer parameter '$name' is required")
    if (value !in range) throw ToolArgumentException("$name must be between ${range.first} and ${range.last}")
    return value
}

class SetAlarmTool(private val clock: ClockApp) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Sets an alarm in the phone's clock app, with sound and snooze. Use it when the user wants to be woken up " +
            "or wants a loud alarm; for a quiet note at some moment use ${SetReminderTool.NAME} instead. Leave days empty for a one-time alarm.",
        parameters = objectSchema(required = listOf("hour", "minute")) {
            integerProperty("hour", "Hour in 24-hour local time", minimum = 0, maximum = 23)
            integerProperty("minute", "Minute", minimum = 0, maximum = 59)
            stringProperty("label", "Short alarm label")
            stringArrayProperty("days", "Days to repeat on, English weekday names like MONDAY")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val hour = arguments.requireInt("hour", 0..23)
        val minute = arguments.requireInt("minute", 0..59)
        val days = arguments.optionalArray("days").map { parseDay((it as? JsonPrimitive)?.contentOrNull) }.toSet()
        val result = withContext(Dispatchers.Main) { clock.setAlarm(hour, minute, arguments.optionalString("label"), days) }
        val repeat = if (days.isEmpty()) "once" else days.sorted().joinToString { it.name.lowercase() }
        return result.toOutcome("Alarm set for %02d:%02d, %s".format(hour, minute, repeat))
    }

    private fun parseDay(raw: String?): DayOfWeek =
        DayOfWeek.entries.firstOrNull { day -> raw != null && day.name.startsWith(raw.trim().uppercase().take(DAY_PREFIX)) }
            ?: throw ToolArgumentException("Unknown weekday \"$raw\"")

    companion object {
        const val NAME = "set_alarm"
        private const val DAY_PREFIX = 3
    }
}

class SetTimerTool(private val clock: ClockApp) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Starts a countdown timer in the phone's clock app.",
        parameters = objectSchema(required = listOf("seconds")) {
            integerProperty("seconds", "Timer length in seconds", minimum = 1, maximum = MAX_SECONDS)
            stringProperty("label", "Short timer label")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val seconds = arguments.requireInt("seconds", 1..MAX_SECONDS)
        val result = withContext(Dispatchers.Main) { clock.setTimer(seconds, arguments.optionalString("label")) }
        return result.toOutcome("Timer started for $seconds s")
    }

    companion object {
        const val NAME = "set_timer"
        private const val MAX_SECONDS = 86_400
    }
}
