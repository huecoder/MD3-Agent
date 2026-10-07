package dev.huecoder.md3assistant.tools.weather

import kotlin.math.roundToInt

internal object WeatherReport {
    fun format(place: Place, forecast: ForecastResponse): String = buildString {
        appendLine(listOfNotNull(place.name, place.admin1, place.country).distinct().joinToString(", "))
        forecast.current?.let { now ->
            append("Сейчас: ").append(describe(now.code))
            now.temperature?.let { append(", ").append(degrees(it)) }
            now.feelsLike?.let { append(" (ощущается ").append(degrees(it)).append(')') }
            now.humidity?.let { append(", влажность ").append(it).append('%') }
            now.wind?.let { append(", ветер ").append(it.roundToInt()).append(" м/с") }
            appendLine()
        }
        forecast.daily?.let { daily ->
            daily.time.forEachIndexed { day, date ->
                append(date).append(": ").append(describe(daily.code.getOrNull(day)))
                val min = daily.min.getOrNull(day)
                val max = daily.max.getOrNull(day)
                if (min != null && max != null) append(", ").append(degrees(min)).append("…").append(degrees(max))
                daily.precipitation.getOrNull(day)?.let { append(", осадки ").append(it).append('%') }
                appendLine()
            }
        }
    }.trimEnd()

    private fun degrees(value: Double) = "${value.roundToInt()}°C"

    fun describe(code: Int?): String = when (code) {
        0 -> "ясно"
        1 -> "преимущественно ясно"
        2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55 -> "морось"
        56, 57 -> "ледяная морось"
        61 -> "небольшой дождь"
        63 -> "дождь"
        65 -> "сильный дождь"
        66, 67 -> "ледяной дождь"
        71 -> "небольшой снег"
        73 -> "снег"
        75 -> "сильный снег"
        77 -> "снежная крупа"
        80, 81 -> "ливень"
        82 -> "сильный ливень"
        85, 86 -> "снегопад"
        95 -> "гроза"
        96, 99 -> "гроза с градом"
        else -> "нет данных"
    }
}
