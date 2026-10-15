package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
enum class ApiFormat(val defaultBaseUrl: String) {
    OPENAI("https://api.openai.com/v1"),
    ANTHROPIC("https://api.anthropic.com/v1"),
    GEMINI("https://generativelanguage.googleapis.com/v1beta"),
    ELEVENLABS("https://api.elevenlabs.io/v1"),
    ;

    val hasChat: Boolean get() = this != ELEVENLABS

    val hasVoice: Boolean get() = this != ANTHROPIC

    fun adoptBaseUrl(current: String): String = if (current.isBlank() || isDefaultBaseUrl(current)) defaultBaseUrl else current

    companion object {
        val CHAT = entries.filter { it.hasChat }

        val VOICE = entries.filter { it.hasVoice }

        fun isDefaultBaseUrl(url: String) = entries.any { it.defaultBaseUrl == url.trim().removeSuffix("/") }
    }
}
