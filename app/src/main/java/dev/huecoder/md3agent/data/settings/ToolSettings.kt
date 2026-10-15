package dev.huecoder.md3agent.data.settings

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
    val fetch: FetchSettings = FetchSettings(),
    val access: AccessSettings = AccessSettings(),
    val mcpServers: List<McpServer> = emptyList(),
    val memory: MemorySettings = MemorySettings(),
    val phoneUse: PhoneUseSettings = PhoneUseSettings(),
) {
    fun permission(toolName: String, default: ToolPermission): ToolPermission = permissions[toolName] ?: default

    fun withPermission(toolName: String, permission: ToolPermission) = copy(permissions = permissions + (toolName to permission))

    fun migrated(): ToolSettings {
        val withPermissions =
            if (legacyDisabled.isEmpty()) this else copy(legacyDisabled = emptySet(), permissions = legacyDisabled.associateWith { ToolPermission.OFF } + permissions)
        return withPermissions.copy(phoneUse = withPermissions.phoneUse.withCurrentPanelDefaults())
    }
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
enum class FetchBackend { DIRECT, JINA_READER, CUSTOM }

@Serializable
data class FetchSettings(
    val backend: FetchBackend = FetchBackend.JINA_READER,
    val jinaApiKey: String = "",
    val customUrl: String = "",
    val customApiKey: String = "",
) {
    companion object {
        const val URL_PLACEHOLDER = "{url}"
        const val JINA_READER_URL = "https://r.jina.ai/$URL_PLACEHOLDER"
    }
}

@Serializable
enum class ShellMode { SANDBOX, TERMUX, SHIZUKU, ROOT }

@Serializable
enum class FileScope { APP, SHARED_STORAGE, ROOT }

@Serializable
data class AccessSettings(val shell: ShellMode = ShellMode.SANDBOX, val files: FileScope = FileScope.APP)

@Serializable
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val apiKey: String = "",
    val headers: List<HeaderEntry> = emptyList(),
    val enabled: Boolean = true,
    val oauth: McpOAuthSession? = null,
) {
    fun requestHeaders(): List<HeaderEntry> =
        headers.filter { it.name.isNotBlank() } + listOfNotNull(apiKey.trim().takeIf { it.isNotEmpty() }?.let { HeaderEntry(AUTHORIZATION, "Bearer $it") })

    fun withoutTokenValues(): McpServer = copy(oauth = oauth?.copy(accessToken = "", refreshToken = null, expiresAt = null))

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}

@Serializable
data class McpOAuthSession(
    val clientId: String,
    val clientSecret: String? = null,
    val tokenEndpoint: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
)

@Serializable
enum class EmbeddingKind { OFF, GEMINI, OPENAI }

@Serializable
data class MemorySettings(
    val embeddingKind: EmbeddingKind = EmbeddingKind.OFF,
    val embeddingBaseUrl: String = "",
    val embeddingApiKey: String = "",
    val embeddingModel: String = "",
) {
    val embeddingsReady: Boolean
        get() = embeddingKind != EmbeddingKind.OFF && embeddingApiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()

    val model: String
        get() = embeddingModel.trim().ifEmpty { if (embeddingKind == EmbeddingKind.GEMINI) GEMINI_MODEL else "" }

    val embeddingSignature: String
        get() = "$embeddingKind:$model"

    val baseUrl: String
        get() = embeddingBaseUrl.trim().ifEmpty { if (embeddingKind == EmbeddingKind.GEMINI) GEMINI_BASE_URL else "" }

    companion object {
        const val GEMINI_MODEL = "gemini-embedding-2"
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val GEMINI_KEY_URL = "https://aistudio.google.com/apikey"
    }
}

@Serializable
enum class TouchGuard { BLOCK, OBSERVE }

@Serializable
enum class TapSound { OFF, SOFT_A, SOFT_B, MOUSE }

@Serializable
enum class AgentPlacement { BESIDE_CURSOR, ON_CURSOR, HIDDEN }

@Serializable
enum class CursorShape { BIBATA_MODERN, BIBATA_ORIGINAL, ROUNDED }

@Serializable
data class PhoneUseSettings(
    val overlay: Boolean = true,
    val touchGuard: TouchGuard = TouchGuard.BLOCK,
    val keepAwake: Boolean = true,
    val dimScreen: Boolean = false,
    val panelOpacity: Float = DEFAULT_PANEL_OPACITY,
    val panelWidth: Float = DEFAULT_PANEL_WIDTH,
    val panelHeight: Float = DEFAULT_PANEL_HEIGHT,
    val cursorSize: Float = DEFAULT_CURSOR_SIZE,
    val cursorShape: CursorShape = CursorShape.BIBATA_MODERN,
    val cursorCaption: Boolean = true,
    val cursorTrail: Boolean = false,
    val agentPlacement: AgentPlacement = AgentPlacement.BESIDE_CURSOR,
    val tapSound: TapSound = TapSound.SOFT_A,
    val soundVolume: Float = DEFAULT_SOUND_VOLUME,
) {
    fun withCurrentPanelDefaults(): PhoneUseSettings =
        if (panelOpacity == FIRST_BUILD_PANEL_OPACITY && panelWidth == FIRST_BUILD_PANEL_WIDTH) {
            copy(panelOpacity = DEFAULT_PANEL_OPACITY, panelWidth = DEFAULT_PANEL_WIDTH)
        } else {
            this
        }

    companion object {
        const val FIRST_BUILD_PANEL_OPACITY = 0.82f
        const val FIRST_BUILD_PANEL_WIDTH = 0.94f
        const val MIN_PANEL_OPACITY = 0.3f
        const val DEFAULT_PANEL_OPACITY = 1f
        const val MIN_PANEL_WIDTH = 0.6f
        const val DEFAULT_PANEL_WIDTH = 0.75f
        const val MIN_PANEL_HEIGHT = 0.15f
        const val MAX_PANEL_HEIGHT = 0.5f
        const val DEFAULT_PANEL_HEIGHT = 0.3f
        const val MIN_CURSOR_SIZE = 0.7f
        const val MAX_CURSOR_SIZE = 1.8f
        const val DEFAULT_CURSOR_SIZE = 1f
        const val DEFAULT_SOUND_VOLUME = 0.6f
    }
}
