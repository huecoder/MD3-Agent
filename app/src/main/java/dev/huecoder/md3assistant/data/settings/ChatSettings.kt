package dev.huecoder.md3assistant.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class ChatSettings(
    val showTokenUsage: Boolean = false,
)

@Serializable
data class SpeechSettings(
    val providerId: String? = null,
    val model: String = DEFAULT_MODEL,
    val language: String = DEFAULT_LANGUAGE,
) {
    companion object {
        const val DEFAULT_MODEL = "whisper-1"
        const val DEFAULT_LANGUAGE = "ru"
    }
}
