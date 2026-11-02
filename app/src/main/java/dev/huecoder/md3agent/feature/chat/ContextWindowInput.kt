package dev.huecoder.md3agent.feature.chat

import kotlin.math.roundToLong

object ContextWindowInput {
    private const val MIN_TOKENS = 1_000L
    private const val MAX_TOKENS = 100_000_000L
    private const val THOUSAND = 1_000.0
    private const val MILLION = 1_000_000.0
    private const val THOUSAND_SUFFIXES = "kк"
    private val NUMBER = Regex("""(\d[\d.,]*)([kmкм]?)""")

    fun parse(raw: String): Int? {
        val text = raw.lowercase().filterNot { it.isWhitespace() || it == '_' }
        val match = NUMBER.matchEntire(text) ?: return null
        val (digits, suffix) = match.destructured
        val tokens = when {
            suffix.isEmpty() -> digits.filter(Char::isDigit).toDoubleOrNull()
            suffix.single() in THOUSAND_SUFFIXES -> digits.replace(',', '.').toDoubleOrNull()?.times(THOUSAND)
            else -> digits.replace(',', '.').toDoubleOrNull()?.times(MILLION)
        } ?: return null
        return tokens.roundToLong().takeIf { it in MIN_TOKENS..MAX_TOKENS }?.toInt()
    }
}
