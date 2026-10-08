package dev.huecoder.md3agent.tools.weather

import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.fetchText
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolArgumentException
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.requireString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class WeatherTool(private val httpClients: HttpClientProvider) : Tool {
    private val json = Json { ignoreUnknownKeys = true }

    override val spec = ToolSpec(
        name = NAME,
        description = "Current weather and daily forecast for a place.",
        parameters = objectSchema(required = listOf("location")) {
            stringProperty("location", "City or town, for example \"Yekaterinburg\"")
            integerProperty("days", "How many days of forecast to return", minimum = 1, maximum = MAX_DAYS)
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val query = arguments.requireString("location")
        val days = (arguments.optionalInt("days") ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)
        val place = geocode(query, settings) ?: return ToolOutcome.error("Could not find a place called \"$query\"")
        val forecast = forecast(place, days, settings)
        return ToolOutcome(WeatherReport.format(place, forecast))
    }

    private suspend fun geocode(query: String, settings: ToolSettings): Place? {
        val url = endpoint(settings.weather.geocodingUrl).newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        val body = httpClients.current().fetchText(Request.Builder().url(url).build())
        return json.decodeFromString(GeocodingResponse.serializer(), body).results.firstOrNull()
    }

    private suspend fun forecast(place: Place, days: Int, settings: ToolSettings): ForecastResponse {
        val url = endpoint(settings.weather.forecastUrl).newBuilder()
            .addQueryParameter("latitude", place.latitude.toString())
            .addQueryParameter("longitude", place.longitude.toString())
            .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("wind_speed_unit", "ms")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", days.toString())
            .build()
        val body = httpClients.current().fetchText(Request.Builder().url(url).build())
        return json.decodeFromString(ForecastResponse.serializer(), body)
    }

    private fun endpoint(raw: String): HttpUrl =
        raw.trim().toHttpUrlOrNull() ?: throw ToolArgumentException("Invalid address in weather settings: \"$raw\"")

    companion object {
        const val NAME = "get_weather"
        private const val DEFAULT_DAYS = 3
        private const val MAX_DAYS = 7
    }
}

@Serializable
internal data class GeocodingResponse(val results: List<Place> = emptyList())

@Serializable
internal data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

@Serializable
internal data class ForecastResponse(val current: Current? = null, val daily: Daily? = null) {
    @Serializable
    data class Current(
        @SerialName("temperature_2m") val temperature: Double? = null,
        @SerialName("apparent_temperature") val feelsLike: Double? = null,
        @SerialName("relative_humidity_2m") val humidity: Int? = null,
        @SerialName("weather_code") val code: Int? = null,
        @SerialName("wind_speed_10m") val wind: Double? = null,
    )

    @Serializable
    data class Daily(
        val time: List<String> = emptyList(),
        @SerialName("weather_code") val code: List<Int?> = emptyList(),
        @SerialName("temperature_2m_max") val max: List<Double?> = emptyList(),
        @SerialName("temperature_2m_min") val min: List<Double?> = emptyList(),
        @SerialName("precipitation_probability_max") val precipitation: List<Int?> = emptyList(),
    )
}
