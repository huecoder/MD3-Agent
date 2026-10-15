package dev.huecoder.md3agent.feature.chat

internal object ComposerStacking {
    const val UNSTACK_MARGIN_CHARS = 6

    fun isStacked(wrappedAt: Int?, text: String): Boolean = wrappedAt != null || '\n' in text

    fun wrappedAfter(wrappedAt: Int?, text: String, lineCount: Int): Int? =
        if (wrappedAt == null && lineCount > 1 && '\n' !in text) text.length else wrappedAt

    fun settle(wrappedAt: Int?, text: String): Int? = when {
        wrappedAt == null -> null
        text.isEmpty() -> null
        text.length < wrappedAt - UNSTACK_MARGIN_CHARS -> null
        else -> wrappedAt
    }
}
