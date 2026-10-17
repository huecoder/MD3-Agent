package com.mwagent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class ChatSettings(
    val showTokenUsage: Boolean = false,
    val autoTitle: Boolean = true,
    val typing: TypingStyle = TypingStyle(),
)

@Serializable
enum class TypingMode { FADE, TYPEWRITER, RAW }

@Serializable
data class TypingStyle(
    val mode: TypingMode = TypingMode.FADE,
    val charsPerSecond: Int = DEFAULT_CHARS_PER_SECOND,
) {
    val isAnimated: Boolean get() = mode != TypingMode.RAW

    companion object {
        const val MIN_CHARS_PER_SECOND = 30
        const val MAX_CHARS_PER_SECOND = 300
        const val DEFAULT_CHARS_PER_SECOND = 90
    }
}
