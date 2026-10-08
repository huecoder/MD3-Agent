package dev.huecoder.md3agent.data.reminders

import dev.huecoder.md3agent.data.db.ReminderDao
import dev.huecoder.md3agent.data.db.ReminderEntity
import dev.huecoder.md3agent.system.reminders.ReminderAlarms
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ReminderRepository(
    private val dao: ReminderDao,
    private val alarms: ReminderAlarms,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val canScheduleExact: Boolean get() = alarms.canScheduleExact

    val canNotify: Boolean get() = alarms.canNotify

    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()

    suspend fun upcoming(): List<ReminderEntity> = dao.listAll()

    suspend fun create(text: String, triggerAt: Long, chatId: String?): ReminderEntity {
        val now = clock()
        require(text.isNotBlank()) { "Reminder text is empty" }
        require(text.length <= MAX_TEXT) { "Reminder text is longer than $MAX_TEXT characters" }
        require(triggerAt > now) { "The reminder time is in the past" }
        require(triggerAt - now <= MAX_AHEAD_MS) { "Reminders can be set at most a year ahead" }
        val reminder = ReminderEntity(
            id = UUID.randomUUID().toString().take(ID_LENGTH),
            text = text.trim(),
            triggerAt = triggerAt,
            chatId = chatId,
            createdAt = now,
        )
        dao.insert(reminder)
        alarms.schedule(reminder)
        return reminder
    }

    suspend fun cancel(id: String): Boolean {
        alarms.cancel(id)
        return dao.delete(id) > 0
    }

    suspend fun fire(id: String) {
        val reminder = dao.get(id) ?: return
        alarms.notify(reminder)
        dao.delete(id)
    }

    suspend fun restore() {
        val now = clock()
        dao.listAll().forEach { reminder ->
            if (reminder.triggerAt <= now) fire(reminder.id) else alarms.schedule(reminder)
        }
    }

    companion object {
        const val MAX_TEXT = 500
        private const val ID_LENGTH = 8
        private const val MAX_AHEAD_MS = 366L * 24 * 60 * 60 * 1000
    }
}
