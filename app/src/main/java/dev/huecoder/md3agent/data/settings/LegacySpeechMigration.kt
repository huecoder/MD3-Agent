package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
internal data class LegacySpeechSettings(
    val providerId: String? = null,
    val model: String = VoiceProvider.DEFAULT_TRANSCRIPTION_MODEL,
    val language: String = "",
)

internal object LegacySpeechMigration {
    fun toVoice(legacy: LegacySpeechSettings, providers: List<ProviderProfile>, activeProviderId: String?): VoiceSettings {
        val source = providers.firstOrNull { it.id == legacy.providerId }
            ?: providers.firstOrNull { it.id == activeProviderId }
            ?: return VoiceSettings(language = legacy.language)
        val voiceProvider = VoiceProvider(
            id = source.id,
            name = source.name,
            baseUrl = source.baseUrl,
            apiKey = source.apiKey,
            extraHeaders = source.extraHeaders,
            transcriptionModel = legacy.model,
        )
        return VoiceSettings(providers = listOf(voiceProvider), activeProviderId = voiceProvider.id, language = legacy.language)
    }
}
