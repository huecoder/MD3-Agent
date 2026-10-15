package dev.huecoder.md3agent.data.settings

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
    val speechVoiceName: String = "",
    val format: ApiFormat = ApiFormat.OPENAI,
) {
    fun asEndpointProfile() =
        ProviderProfile(id = id, name = name, baseUrl = baseUrl, apiKey = apiKey, extraHeaders = extraHeaders, format = format)

    companion object {
        const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-1"
        const val DEFAULT_SPEECH_MODEL = "gpt-4o-mini-tts"
        const val DEFAULT_SPEECH_VOICE = "alloy"

        fun defaultTranscriptionModel(format: ApiFormat) = when (format) {
            ApiFormat.GEMINI -> "gemini-2.5-flash"
            ApiFormat.ELEVENLABS -> "scribe_v2"
            else -> DEFAULT_TRANSCRIPTION_MODEL
        }

        fun defaultSpeechModel(format: ApiFormat) = when (format) {
            ApiFormat.GEMINI -> "gemini-2.5-flash-preview-tts"
            ApiFormat.ELEVENLABS -> "eleven_multilingual_v2"
            else -> DEFAULT_SPEECH_MODEL
        }

        fun defaultSpeechVoice(format: ApiFormat) = when (format) {
            ApiFormat.GEMINI -> "Kore"
            ApiFormat.ELEVENLABS -> "21m00Tcm4TlvDq8ikWAM"
            else -> DEFAULT_SPEECH_VOICE
        }
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
    val speechProviderId: String? = null,
    val language: String = "",
    val speakReplies: SpeakReplies = SpeakReplies.AFTER_VOICE_INPUT,
    val speechOutput: SpeechOutput = SpeechOutput.SYSTEM,
    val invokeInput: InvokeInput = InvokeInput.TEXT,
) {
    val transcriptionProvider: VoiceProvider?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()

    val speechProvider: VoiceProvider?
        get() = providers.firstOrNull { it.id == speechProviderId } ?: transcriptionProvider

    fun speechProviderFor(choice: AgentVoice): VoiceProvider? {
        val provider = providers.firstOrNull { it.id == choice.providerId }
            ?: speechProvider.takeIf { speechOutput == SpeechOutput.PROVIDER }
            ?: return null
        return if (choice.voiceId.isBlank()) provider else provider.copy(speechVoice = choice.voiceId, speechVoiceName = choice.voiceName)
    }
}
