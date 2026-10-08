package dev.huecoder.md3agent.voice

class SpeechChunker {
    private var consumed = 0

    fun ready(streamed: String): List<String> {
        val cut = lastBoundary(streamed)
        if (cut <= consumed) return emptyList()
        return take(streamed, cut)
    }

    fun rest(full: String): List<String> = if (full.length <= consumed) emptyList() else take(full, full.length)

    private fun take(text: String, end: Int): List<String> {
        val piece = SpeakableText.of(text.substring(consumed, end))
        consumed = end
        return splitLong(piece)
    }

    private fun lastBoundary(text: String): Int {
        val fences = FENCE.findAll(text).toList()
        val end = if (fences.size % 2 == 1) fences.last().range.first else text.length
        var index = end - 1
        while (index >= consumed) {
            val char = text[index]
            if (char == '\n' || (char in SENTENCE_ENDS && text.getOrNull(index + 1)?.isWhitespace() == true)) {
                return if (index + 1 - consumed >= MIN_CHUNK || char == '\n') index + 1 else consumed
            }
            index--
        }
        return consumed
    }

    private fun splitLong(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        if (text.length <= MAX_CHUNK) return listOf(text)
        val parts = mutableListOf<String>()
        var rest = text
        while (rest.length > MAX_CHUNK) {
            val at = rest.lastIndexOf(' ', MAX_CHUNK).takeIf { it > 0 } ?: MAX_CHUNK
            parts += rest.substring(0, at).trim()
            rest = rest.substring(at).trim()
        }
        if (rest.isNotBlank()) parts += rest
        return parts
    }

    private companion object {
        const val MIN_CHUNK = 24
        const val MAX_CHUNK = 400
        val SENTENCE_ENDS = setOf('.', '!', '?', '…', ':', ';')
        val FENCE = Regex("```")
    }
}

object SpeakableText {
    private val codeBlock = Regex("```[\\s\\S]*?```")
    private val inlineCode = Regex("`([^`]*)`")
    private val image = Regex("!\\[[^]]*]\\([^)]*\\)")
    private val link = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val bareUrl = Regex("https?://\\S+")
    private val linePrefix = Regex("(?m)^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d+[.)]\\s+)")
    private val emphasis = Regex("[*_~]{1,3}")
    private val tableRule = Regex("(?m)^\\s*\\|?[\\s:|-]+\\|?\\s*$")
    private val spaces = Regex("\\s+")

    fun of(markdown: String): String = markdown
        .replace(codeBlock, " ")
        .replace(image, " ")
        .replace(link, "$1")
        .replace(bareUrl, " ")
        .replace(inlineCode, "$1")
        .replace(tableRule, " ")
        .replace(linePrefix, "")
        .replace(emphasis, "")
        .replace('|', ' ')
        .replace(spaces, " ")
        .trim()
}
