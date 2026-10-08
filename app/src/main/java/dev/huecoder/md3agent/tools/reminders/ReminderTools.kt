package dev.huecoder.md3agent.tools.reminders

import dev.huecoder.md3agent.data.db.ReminderEntity
import dev.huecoder.md3agent.data.reminders.ReminderRepository
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.tools.ContextualTool
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolArgumentException
import dev.huecoder.md3agent.tools.ToolContext
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.optionalString
import dev.huecoder.md3agent.tools.requireString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class ReminderClock(val zone: () -> ZoneId = ZoneId::systemDefault, val now: () -> Long = System::currentTimeMillis) {
    fun format(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone()).format(FORMAT)

    fun parseLocal(raw: String): Long = try {
        LocalDateTime.parse(raw.trim().replace(' ', 'T')).atZone(zone()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        throw ToolArgumentException("at must look like 2026-10-08T18:30 in the user's local time")
    }

    private companion object {
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm", Locale.ENGLISH)
    }
}

private fun ReminderEntity.describe(clock: ReminderClock) = "[$id] ${clock.format(triggerAt)}: $text"

class SetReminderTool(private val reminders: ReminderRepository, private val clock: ReminderClock = ReminderClock()) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Schedules a reminder: at the given moment the user gets a notification with your text, and tapping it " +
            "opens this chat. Give either an exact local time in \"at\" or a delay in \"in_minutes\", not both. Write the text " +
            "as the note the user should read, in their language.",
        parameters = objectSchema(required = listOf("text")) {
            stringProperty("text", "What to remind about, up to ${ReminderRepository.MAX_TEXT} characters")
            stringProperty("at", "Local date and time, ISO format like 2026-10-08T18:30")
            integerProperty("in_minutes", "Minutes from now", minimum = 1, maximum = MAX_MINUTES)
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val at = arguments.optionalString("at")
        val minutes = arguments.optionalInt("in_minutes")
        val triggerAt = when {
            at != null && minutes != null -> throw ToolArgumentException("Give either at or in_minutes, not both")
            at != null -> clock.parseLocal(at)
            minutes != null -> clock.now() + minutes.coerceIn(1, MAX_MINUTES) * MILLIS_PER_MINUTE
            else -> throw ToolArgumentException("Either at or in_minutes is required")
        }
        val reminder = try {
            reminders.create(arguments.requireString("text"), triggerAt, context.chatId)
        } catch (e: IllegalArgumentException) {
            return ToolOutcome.error(e.message.orEmpty())
        }
        val notes = buildList {
            if (!reminders.canNotify) add("Notifications are turned off for the app, so the user will not see it until they allow them.")
            if (!reminders.canScheduleExact) add("Exact alarms are not allowed, so it may arrive a few minutes late.")
        }
        return ToolOutcome((listOf("Reminder set ${reminder.describe(clock)}") + notes).joinToString("\n"))
    }

    companion object {
        const val NAME = "set_reminder"
        private const val MAX_MINUTES = 525_600
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

class ListRemindersTool(private val reminders: ReminderRepository, private val clock: ReminderClock = ReminderClock()) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Lists reminders that have not fired yet, with their ids, soonest first.",
        parameters = objectSchema(required = emptyList()) {},
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val upcoming = reminders.upcoming()
        if (upcoming.isEmpty()) return ToolOutcome("No upcoming reminders")
        return ToolOutcome(upcoming.joinToString("\n") { it.describe(clock) })
    }

    companion object {
        const val NAME = "list_reminders"
    }
}

class CancelReminderTool(private val reminders: ReminderRepository) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Cancels a reminder by its id from ${ListRemindersTool.NAME}.",
        parameters = objectSchema(required = listOf("id")) {
            stringProperty("id", "Reminder id")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val id = arguments.requireString("id")
        return if (reminders.cancel(id)) ToolOutcome("Reminder $id cancelled") else ToolOutcome.error("No upcoming reminder with id $id")
    }

    companion object {
        const val NAME = "cancel_reminder"
    }
}
