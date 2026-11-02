package dev.huecoder.md3agent.data.chat

object CaseBlindPattern {
    private const val GLOB_SPECIALS = "*?["

    fun containing(query: String): String = buildString {
        append('*')
        query.trim().forEach { char ->
            val lower = char.lowercaseChar()
            val upper = char.uppercaseChar()
            when {
                lower != upper -> append('[').append(lower).append(upper).append(']')
                char in GLOB_SPECIALS -> append('[').append(char).append(']')
                else -> append(char)
            }
        }
        append('*')
    }
}
