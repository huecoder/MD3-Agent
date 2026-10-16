package dev.huecoder.md3agent.ui.pictures

enum class PictureAlign { START, CENTER, END }

enum class PictureGrouping { ALBUM, SLIDER }

data class PictureOptions(
    val align: PictureAlign? = null,
    val widthShare: Float? = null,
    val grouping: PictureGrouping? = null,
) {
    companion object {
        const val MIN_SHARE = 0.1f
        const val MAX_SHARE = 1f
        val NONE = PictureOptions()

        private val ALIGNS = mapOf(
            "left" to PictureAlign.START,
            "start" to PictureAlign.START,
            "center" to PictureAlign.CENTER,
            "centre" to PictureAlign.CENTER,
            "middle" to PictureAlign.CENTER,
            "right" to PictureAlign.END,
            "end" to PictureAlign.END,
        )
        private val SIZES = mapOf("small" to 0.4f, "medium" to 0.6f, "large" to 0.8f, "full" to 1f)
        private val GROUPINGS = mapOf(
            "album" to PictureGrouping.ALBUM,
            "grid" to PictureGrouping.ALBUM,
            "slider" to PictureGrouping.SLIDER,
            "carousel" to PictureGrouping.SLIDER,
            "row" to PictureGrouping.SLIDER,
        )
        private val SEPARATORS = Regex("""[\s,;]+""")
        private val PERCENT = Regex("""(\d{1,3})%""")
        private val KEYS = setOf("align", "position", "pos", "width", "size", "layout")

        fun parse(title: String?): PictureOptions? {
            val tokens = title.orEmpty().lowercase().split(SEPARATORS).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            var options = NONE
            for (token in tokens) {
                val value = token.substringAfter('=', token).takeIf { token.substringBefore('=') in KEYS || '=' !in token } ?: return null
                options = when {
                    value in ALIGNS -> options.copy(align = options.align ?: ALIGNS.getValue(value))
                    value in SIZES -> options.copy(widthShare = options.widthShare ?: SIZES.getValue(value))
                    value in GROUPINGS -> options.copy(grouping = options.grouping ?: GROUPINGS.getValue(value))
                    PERCENT.matches(value) -> options.copy(widthShare = options.widthShare ?: percent(value))
                    else -> return null
                }
            }
            return options
        }

        private fun percent(value: String): Float =
            (PERCENT.matchEntire(value)!!.groupValues[1].toFloat() / PERCENT_BASE).coerceIn(MIN_SHARE, MAX_SHARE)

        private const val PERCENT_BASE = 100f
    }
}

data class Picture(val url: String, val description: String, val options: PictureOptions = PictureOptions.NONE)

object PictureSyntax {
    fun line(line: String): List<Picture>? {
        var index = skipSpaces(line, 0)
        if (index > MAX_INDENT) return null
        val pictures = mutableListOf<Picture>()
        while (index < line.length) {
            val (picture, next) = token(line, index) ?: return null
            pictures += picture
            index = skipSpaces(line, next)
        }
        return pictures.takeIf { it.isNotEmpty() }
    }

    fun single(source: String): Picture? = token(source, 0)?.takeIf { (_, next) -> next == source.length }?.first

    private fun token(text: String, start: Int): Pair<Picture, Int>? {
        if (!text.startsWith("![", start)) return null
        val altEnd = closingBracket(text, start + 2) ?: return null
        if (text.getOrNull(altEnd + 1) != '(') return null
        var index = skipSpaces(text, altEnd + 2)
        val destination = destination(text, index) ?: return null
        index = skipSpaces(text, destination.second)
        var title: String? = null
        if (index < text.length && text[index] != ')') {
            val parsed = title(text, index) ?: return null
            title = parsed.first
            index = skipSpaces(text, parsed.second)
        }
        if (text.getOrNull(index) != ')' || destination.first.isEmpty()) return null
        val alt = text.substring(start + 2, altEnd).replace(ESCAPED_BRACKET, "$1")
        return Picture(destination.first, alt, PictureOptions.parse(title) ?: PictureOptions.NONE) to index + 1
    }

    private fun closingBracket(text: String, from: Int): Int? {
        var depth = 0
        var index = from
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index++
                '[' -> depth++
                ']' -> if (depth == 0) return index else depth--
            }
            index++
        }
        return null
    }

    private fun destination(text: String, from: Int): Pair<String, Int>? {
        if (text.getOrNull(from) == '<') {
            val end = text.indexOf('>', from + 1).takeIf { it > 0 } ?: return null
            return text.substring(from + 1, end) to end + 1
        }
        var depth = 0
        var index = from
        while (index < text.length) {
            val char = text[index]
            if (char.isWhitespace()) break
            if (char == '(') depth++
            if (char == ')') {
                if (depth == 0) break
                depth--
            }
            index++
        }
        return text.substring(from, index) to index
    }

    private fun title(text: String, from: Int): Pair<String, Int>? {
        val closing = when (text[from]) {
            '"' -> '"'
            '\'' -> '\''
            '(' -> ')'
            else -> return null
        }
        var index = from + 1
        while (index < text.length) {
            if (text[index] == '\\') index++ else if (text[index] == closing) return text.substring(from + 1, index) to index + 1
            index++
        }
        return null
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var index = from
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private const val MAX_INDENT = 1
    private val ESCAPED_BRACKET = Regex("""\\([\[\]])""")
}
