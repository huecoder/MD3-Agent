package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class ChatSettings(
    val showTokenUsage: Boolean = false,
    val showContext: Boolean = false,
    val autoTitle: Boolean = true,
    val autoRetry: Boolean = false,
    val attachMenu: AttachMenuStyle = AttachMenuStyle.COMPACT,
    val header: ChatHeaderStyle = ChatHeaderStyle.COMPACT,
    val contextDisplay: ContextDisplay = ContextDisplay.NUMBERS,
    val typing: TypingStyle = TypingStyle(),
)

@Serializable
enum class AttachMenuStyle { COMPACT, PANEL }

@Serializable
enum class ChatHeaderStyle { COMPACT, CLASSIC }

@Serializable
enum class ContextDisplay { NUMBERS, RING, MENU }

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
