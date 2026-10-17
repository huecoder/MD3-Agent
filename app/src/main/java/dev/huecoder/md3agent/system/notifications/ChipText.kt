package dev.huecoder.md3agent.system.notifications

import android.content.res.Resources
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ChipContent

data class ChipUnits(
    val second: String,
    val minute: String,
    val hour: String,
    val thousand: String,
    val million: String,
    val decimal: Char,
) {
    companion object {
        fun of(resources: Resources) = ChipUnits(
            second = resources.getString(R.string.chip_unit_second),
            minute = resources.getString(R.string.chip_unit_minute),
            hour = resources.getString(R.string.chip_unit_hour),
            thousand = resources.getString(R.string.chip_unit_thousand),
            million = resources.getString(R.string.chip_unit_million),
            decimal = resources.getString(R.string.chip_decimal).first(),
        )
    }
}

object ChipText {
    const val MAX_CHARS = 7
    private const val SEPARATOR = "|"
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_SECOND = 1_000
    private const val EXACT_BELOW = 100
    private const val TENS_BELOW = 1_000
    private const val DECIMAL_THOUSANDS_BELOW = 10_000
    private const val THOUSAND = 1_000
    private const val MILLION = 1_000_000
    private const val DECIMAL_MILLIONS_BELOW = 10_000_000

    fun working(content: ChipContent, elapsedMs: Long, tokens: Int, units: ChipUnits): String = when (content) {
        ChipContent.TIME -> duration(elapsedMs, units, compact = false)
        ChipContent.TOKENS -> tokens(tokens, units)
        ChipContent.TIME_AND_TOKENS -> combined(elapsedMs, tokens, units)
    }

    fun duration(elapsedMs: Long, units: ChipUnits, compact: Boolean): String {
        val seconds = (elapsedMs / MILLIS_PER_SECOND).coerceAtLeast(0)
        val minutes = seconds / SECONDS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        return when {
            seconds < SECONDS_PER_MINUTE -> "$seconds${units.second}"
            minutes < MINUTES_PER_HOUR ->
                if (compact) "$minutes${units.minute}" else "$minutes${units.minute}${twoDigits(seconds % SECONDS_PER_MINUTE)}${units.second}"
            else -> if (compact) "$hours${units.hour}" else "$hours${units.hour}${twoDigits(minutes % MINUTES_PER_HOUR)}${units.minute}"
        }
    }

    fun tokens(count: Int, units: ChipUnits, withDecimal: Boolean = true): String = when {
        count < EXACT_BELOW -> "${count.coerceAtLeast(0)}"
        count < TENS_BELOW -> "${count / 10 * 10}"
        count < DECIMAL_THOUSANDS_BELOW -> scaled(count, THOUSAND, units.thousand, units.decimal, withDecimal)
        count < MILLION -> "${count / THOUSAND}${units.thousand}"
        count < DECIMAL_MILLIONS_BELOW -> scaled(count, MILLION, units.million, units.decimal, withDecimal)
        else -> "${count / MILLION}${units.million}"
    }

    private fun combined(elapsedMs: Long, tokens: Int, units: ChipUnits): String {
        val time = duration(elapsedMs, units, compact = true)
        if (tokens <= 0) return time
        val full = "$time$SEPARATOR${tokens(tokens, units)}"
        return if (full.length <= MAX_CHARS) full else "$time$SEPARATOR${tokens(tokens, units, withDecimal = false)}"
    }

    private fun scaled(count: Int, unit: Int, suffix: String, decimal: Char, withDecimal: Boolean): String {
        val whole = count / unit
        val tenth = count % unit * 10 / unit
        return if (withDecimal && tenth > 0) "$whole$decimal$tenth$suffix" else "$whole$suffix"
    }

    private fun twoDigits(value: Long): String = value.toString().padStart(2, '0')
}
