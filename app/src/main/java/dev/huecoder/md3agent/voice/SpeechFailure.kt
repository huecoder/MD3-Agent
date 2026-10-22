package dev.huecoder.md3agent.voice

import dev.huecoder.md3agent.net.HttpStatusException

object SpeechFailure {
    private val providerMessage = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    fun describe(error: Exception): String {
        val text = error.message ?: error.javaClass.simpleName
        if (error !is HttpStatusException) return text
        val detail = providerMessage.find(text)?.groupValues?.get(1)?.replace("\\\"", "\"") ?: return text
        return "HTTP ${error.code}: $detail"
    }
}
