package dev.huecoder.md3assistant.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class ToolSettings(
    val disabledTools: Set<String> = emptySet(),
    val weather: WeatherSettings = WeatherSettings(),
    val search: SearchSettings = SearchSettings(),
) {
    fun isEnabled(toolName: String): Boolean = toolName !in disabledTools
}

@Serializable
data class WeatherSettings(
    val forecastUrl: String = DEFAULT_FORECAST_URL,
    val geocodingUrl: String = DEFAULT_GEOCODING_URL,
) {
    companion object {
        const val DEFAULT_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val DEFAULT_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
    }
}

@Serializable
enum class SearchBackend { HTML_SCRAPE, SEARXNG, BRAVE_API }

@Serializable
data class SearchSettings(
    val backend: SearchBackend = SearchBackend.HTML_SCRAPE,
    val searxngUrl: String = "",
    val apiKey: String = "",
)
