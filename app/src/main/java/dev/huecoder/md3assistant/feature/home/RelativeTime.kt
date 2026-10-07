package dev.huecoder.md3assistant.feature.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RelativeTime {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM")

    fun format(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return if (moment.toLocalDate() == LocalDate.now(zone)) moment.format(timeFormat) else moment.format(dateFormat)
    }
}
