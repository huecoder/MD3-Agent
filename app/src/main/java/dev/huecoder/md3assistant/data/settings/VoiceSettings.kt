package dev.huecoder.md3assistant.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class VoiceProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val extraHeaders: List<HeaderEntry> = emptyList(),
    val transcriptionModel: String = DEFAULT_TRANSCRIPTION_MODEL,
    val speechModel: String = DEFAULT_SPEECH_MODEL,
    val speechVoice: String = DEFAULT_SPEECH_VOICE,
) {
    fun asEndpointProfile() = ProviderProfile(id = id, name = name, baseUrl = baseUrl, apiKey = apiKey, extraHeaders = extraHeaders)

    companion object {
        const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-1"
        const val DEFAULT_SPEECH_MODEL = "gpt-4o-mini-tts"
        const val DEFAULT_SPEECH_VOICE = "alloy"
    }
}

@Serializable
enum class SpeechOutput { SYSTEM, PROVIDER }

@Serializable
enum class SpeakReplies { NEVER, AFTER_VOICE_INPUT, ALWAYS }

@Serializable
enum class InvokeInput { TEXT, VOICE }

@Serializable
data class VoiceSettings(
    val providers: List<VoiceProvider> = emptyList(),
    val activeProviderId: String? = null,
    val language: String = "",
    val speakReplies: SpeakReplies = SpeakReplies.AFTER_VOICE_INPUT,
    val speechOutput: SpeechOutput = SpeechOutput.SYSTEM,
    val invokeInput: InvokeInput = InvokeInput.TEXT,
) {
    val activeProvider: VoiceProvider?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()
}
