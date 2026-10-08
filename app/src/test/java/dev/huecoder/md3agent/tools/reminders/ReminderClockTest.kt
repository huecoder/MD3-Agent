package dev.huecoder.md3agent.tools.reminders

import dev.huecoder.md3agent.tools.ToolArgumentException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderClockTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val clock = ReminderClock(zone = { zone })

    @Test
    fun `parses local time in the user's zone`() {
        val expected = ZonedDateTime.of(2026, 10, 8, 18, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, clock.parseLocal("2026-10-08T18:30"))
    }

    @Test
    fun `accepts a space instead of T`() {
        assertEquals(clock.parseLocal("2026-10-08T18:30"), clock.parseLocal(" 2026-10-08 18:30 "))
    }

    @Test(expected = ToolArgumentException::class)
    fun `rejects free text`() {
        clock.parseLocal("tomorrow at six")
    }

    @Test
    fun `formats with weekday`() {
        val millis = ZonedDateTime.of(2026, 10, 8, 9, 5, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Thu 2026-10-08 09:05", clock.format(millis))
    }
}
