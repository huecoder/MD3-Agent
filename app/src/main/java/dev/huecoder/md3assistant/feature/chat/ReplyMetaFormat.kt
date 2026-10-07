package dev.huecoder.md3assistant.feature.chat

import dev.huecoder.md3assistant.llm.TokenUsage
import java.util.Locale

object ReplyMetaFormat {
    private const val MS_PER_SECOND = 1_000.0
    private const val SECONDS_PER_MINUTE = 60
    private const val THOUSAND = 1_000
    private val locale = Locale.forLanguageTag("ru")

    fun duration(millis: Long): String {
        val seconds = millis / MS_PER_SECOND
        if (seconds < SECONDS_PER_MINUTE) return String.format(locale, "%.1f с", seconds)
        val whole = seconds.toLong()
        return String.format(locale, "%d мин %02d с", whole / SECONDS_PER_MINUTE, whole % SECONDS_PER_MINUTE)
    }

    fun usage(usage: TokenUsage): String = "↑ ${tokens(usage.promptTokens)} ↓ ${tokens(usage.completionTokens)}"

    private fun tokens(count: Int): String =
        if (count < THOUSAND) count.toString() else String.format(locale, "%.1fk", count.toDouble() / THOUSAND)
}
