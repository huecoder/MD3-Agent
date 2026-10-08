package dev.huecoder.md3agent.tools.weather

import kotlin.math.roundToInt

internal object WeatherReport {
    fun format(place: Place, forecast: ForecastResponse): String = buildString {
        appendLine(listOfNotNull(place.name, place.admin1, place.country).distinct().joinToString(", "))
        forecast.current?.let { now ->
            append("Now: ").append(describe(now.code))
            now.temperature?.let { append(", ").append(degrees(it)) }
            now.feelsLike?.let { append(" (feels like ").append(degrees(it)).append(')') }
            now.humidity?.let { append(", humidity ").append(it).append('%') }
            now.wind?.let { append(", wind ").append(it.roundToInt()).append(" m/s") }
            appendLine()
        }
        forecast.daily?.let { daily ->
            daily.time.forEachIndexed { day, date ->
                append(date).append(": ").append(describe(daily.code.getOrNull(day)))
                val min = daily.min.getOrNull(day)
                val max = daily.max.getOrNull(day)
                if (min != null && max != null) append(", ").append(degrees(min)).append("…").append(degrees(max))
                daily.precipitation.getOrNull(day)?.let { append(", precipitation ").append(it).append('%') }
                appendLine()
            }
        }
    }.trimEnd()

    private fun degrees(value: Double) = "${value.roundToInt()}°C"

    fun describe(code: Int?): String = when (code) {
        0 -> "clear"
        1 -> "mostly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain"
        71 -> "light snow"
        73 -> "snow"
        75 -> "heavy snow"
        77 -> "snow grains"
        80, 81 -> "rain showers"
        82 -> "heavy rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "no data"
    }
}
