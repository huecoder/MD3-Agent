package dev.huecoder.md3assistant.data.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ToolPermission { OFF, ASK, AUTO }

@Serializable
data class ToolSettings(
    @SerialName("disabledTools") private val legacyDisabled: Set<String> = emptySet(),
    val permissions: Map<String, ToolPermission> = emptyMap(),
    val weather: WeatherSettings = WeatherSettings(),
    val search: SearchSettings = SearchSettings(),
    val access: AccessSettings = AccessSettings(),
) {
    fun permission(toolName: String, default: ToolPermission): ToolPermission = permissions[toolName] ?: default

    fun withPermission(toolName: String, permission: ToolPermission) = copy(permissions = permissions + (toolName to permission))

    fun migrated(): ToolSettings =
        if (legacyDisabled.isEmpty()) this else copy(legacyDisabled = emptySet(), permissions = legacyDisabled.associateWith { ToolPermission.OFF } + permissions)
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

@Serializable
enum class ShellMode { SANDBOX, TERMUX, SHIZUKU, ROOT }

@Serializable
data class AccessSettings(val shell: ShellMode = ShellMode.SANDBOX)
