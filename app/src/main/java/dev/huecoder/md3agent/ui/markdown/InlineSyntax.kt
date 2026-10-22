package dev.huecoder.md3agent.ui.markdown

data class Footnote(val number: Int, val markdown: String)

class PreparedMarkdown(val markdown: String, val footnotes: List<Footnote>)

object InlineSyntax {
    const val HIGHLIGHT_OPEN = '\uE000'
    const val HIGHLIGHT_CLOSE = '\uE001'
    private const val FOOTNOTE_BASE = 0xE100
    private const val MAX_FOOTNOTES = 999
    private const val HIGHLIGHT_DELIMITER = "=="
    private const val FOOTNOTE_OPENING = "[^"
    private const val BACKTICK = '`'
    private const val ESCAPE = '\\'
    private const val ESCAPE_LENGTH = 2
    private val DEFINITION = Regex("""^ {0,3}\[\^([^\]\s]+)]:[ \t]*(.*)$""")
    private val CONTINUATION = Regex("""^(?: {2,}|\t)\S""")

    fun footnoteMarker(number: Int): Char = Char(FOOTNOTE_BASE + number)

    fun footnoteNumber(char: Char): Int? = (char.code - FOOTNOTE_BASE).takeIf { it in 1..MAX_FOOTNOTES }

    fun isMarker(char: Char): Boolean = char == HIGHLIGHT_OPEN || char == HIGHLIGHT_CLOSE || footnoteNumber(char) != null

    fun prepare(markdown: String): PreparedMarkdown {
        if (!markdown.contains(HIGHLIGHT_DELIMITER) && !markdown.contains(FOOTNOTE_OPENING)) return PreparedMarkdown(markdown, emptyList())
        val (body, definitions) = extractDefinitions(markdown)
        val marker = LineMarker(definitions)
        return PreparedMarkdown(mapOutsideFences(body, marker::mark), marker.footnotes())
    }

    private fun extractDefinitions(markdown: String): Pair<String, Map<String, String>> {
        val definitions = linkedMapOf<String, String>()
        val kept = StringBuilder(markdown.length)
        val lines = markdown.split('\n')
        var fence: String? = null
        var index = 0
        while (index < lines.size) {
            val line = lines[index++]
            val insideFence = fence != null
            fence = MarkdownFences.next(fence, line.trimEnd('\r'))
            val definition = if (insideFence || fence != null) null else DEFINITION.matchEntire(line.trimEnd('\r'))
            if (definition == null) {
                kept.append(line)
                if (index < lines.size) kept.append('\n')
                continue
            }
            val body = mutableListOf(definition.groupValues[2])
            while (index < lines.size && CONTINUATION.containsMatchIn(lines[index])) body += lines[index++]
            val text = body.joinToString("\n") { it.trim() }.trim()
            if (text.isNotEmpty()) definitions.putIfAbsent(definition.groupValues[1], text)
        }
        return kept.toString() to definitions
    }

    private fun mapOutsideFences(text: String, transform: (String) -> String): String {
        val out = StringBuilder(text.length)
        var fence: String? = null
        var start = 0
        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            val line = text.substring(start, end)
            val insideFence = fence != null
            fence = MarkdownFences.next(fence, line.trimEnd('\r'))
            out.append(if (insideFence || fence != null) line else transform(line))
            if (end < text.length) out.append('\n')
            start = end + 1
        }
        return out.toString()
    }

    private class LineMarker(private val definitions: Map<String, String>) {
        private val numbers = linkedMapOf<String, Int>()
        private val out = StringBuilder()

        fun mark(line: String): String {
            if (!line.contains(HIGHLIGHT_DELIMITER) && !line.contains(FOOTNOTE_OPENING)) return line
            out.setLength(0)
            markRange(line, 0, line.length)
            return out.toString()
        }

        fun footnotes(): List<Footnote> {
            val referenced = numbers.map { (label, number) -> Footnote(number, definitions.getValue(label)) }
            val unreferenced = definitions.filterKeys { it !in numbers }.values.mapIndexed { index, text -> Footnote(numbers.size + index + 1, text) }
            return referenced + unreferenced
        }

        private fun markRange(text: String, from: Int, to: Int) {
            var index = from
            while (index < to) {
                index = when {
                    text[index] == BACKTICK -> copyCodeSpan(text, index, to)
                    text[index] == ESCAPE -> copy(text, index, minOf(ESCAPE_LENGTH, to - index))
                    text.startsWith(FOOTNOTE_OPENING, index) -> footnoteAt(text, index, to)
                    text.startsWith(HIGHLIGHT_DELIMITER, index) -> highlightAt(text, index, to)
                    else -> copy(text, index, 1)
                }
            }
        }

        private fun copy(text: String, from: Int, length: Int): Int {
            out.append(text, from, from + length)
            return from + length
        }

        private fun copyCodeSpan(text: String, from: Int, to: Int): Int {
            val ticks = text.substring(from, to).takeWhile { it == BACKTICK }.length
            val close = text.indexOf(BACKTICK.toString().repeat(ticks), from + ticks)
            val end = if (close < 0 || close + ticks > to) from + ticks else close + ticks
            return copy(text, from, end - from)
        }

        private fun footnoteAt(text: String, from: Int, to: Int): Int {
            val close = text.indexOf(']', from + FOOTNOTE_OPENING.length)
            if (close < 0 || close >= to) return copy(text, from, 1)
            val label = text.substring(from + FOOTNOTE_OPENING.length, close)
            if (label !in definitions || label.any(Char::isWhitespace)) return copy(text, from, 1)
            val number = numbers.getOrPut(label) { numbers.size + 1 }
            if (number > MAX_FOOTNOTES) return copy(text, from, 1)
            out.append(footnoteMarker(number))
            return close + 1
        }

        private fun highlightAt(text: String, from: Int, to: Int): Int {
            val contentStart = from + HIGHLIGHT_DELIMITER.length
            val canOpen = (from == 0 || !text[from - 1].isLetterOrDigit()) &&
                contentStart < to && !text[contentStart].isWhitespace() && text[contentStart] != '='
            val close = if (canOpen) closingDelimiter(text, contentStart, to) else -1
            if (close < 0) return copy(text, from, HIGHLIGHT_DELIMITER.length)
            out.append(HIGHLIGHT_OPEN)
            markRange(text, contentStart, close)
            out.append(HIGHLIGHT_CLOSE)
            return close + HIGHLIGHT_DELIMITER.length
        }

        private fun closingDelimiter(text: String, from: Int, to: Int): Int {
            var index = text.indexOf(HIGHLIGHT_DELIMITER, from)
            while (index >= 0 && index + HIGHLIGHT_DELIMITER.length <= to) {
                val before = text[index - 1]
                val after = text.getOrNull(index + HIGHLIGHT_DELIMITER.length)
                if (index > from && !before.isWhitespace() && before != '=' && after != '=') return index
                index = text.indexOf(HIGHLIGHT_DELIMITER, index + 1)
            }
            return -1
        }
    }
}
