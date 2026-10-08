package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.ui.text.UiText

object ReplyMetaFormat {
    private const val MS_PER_SECOND = 1_000.0
    private const val SECONDS_PER_MINUTE = 60
    private const val THOUSAND = 1_000

    fun duration(millis: Long): UiText {
        val seconds = millis / MS_PER_SECOND
        if (seconds < SECONDS_PER_MINUTE) return UiText.Resource(R.string.chat_meta_duration_seconds, listOf(seconds))
        val whole = seconds.toLong()
        return UiText.Resource(R.string.chat_meta_duration_minutes, listOf(whole / SECONDS_PER_MINUTE, whole % SECONDS_PER_MINUTE))
    }

    fun usage(usage: TokenUsage): UiText =
        UiText.Resource(R.string.chat_meta_usage, listOf(tokens(usage.promptTokens), tokens(usage.completionTokens)))

    private fun tokens(count: Int): UiText =
        if (count < THOUSAND) UiText.Raw(count.toString()) else UiText.Resource(R.string.chat_meta_tokens_thousands, listOf(count.toDouble() / THOUSAND))
}
