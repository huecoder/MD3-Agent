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
    val contextWindows: Map<String, Int> = emptyMap(),
) {
    fun contextWindow(selection: ModelSelection): Int? = contextWindows[contextWindowKey(selection)]

    fun withContextWindow(selection: ModelSelection, tokens: Int?): ChatSettings {
        val key = contextWindowKey(selection)
        return copy(contextWindows = if (tokens == null) contextWindows - key else contextWindows + (key to tokens))
    }

    private fun contextWindowKey(selection: ModelSelection) = "${selection.providerId}:${selection.modelId}"
}

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
