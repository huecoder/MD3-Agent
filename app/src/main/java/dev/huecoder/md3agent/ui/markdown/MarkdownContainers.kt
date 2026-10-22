package dev.huecoder.md3agent.ui.markdown

enum class CalloutFold { NONE, OPEN, CLOSED }

internal sealed interface Region {
    val start: Int
    val end: Int

    data class Plain(override val start: Int, override val end: Int) : Region

    data class Details(
        override val start: Int,
        override val end: Int,
        val summary: String?,
        val isOpen: Boolean,
        val bodyStart: Int,
        val bodyEnd: Int,
    ) : Region

    data class Callout(
        override val start: Int,
        override val end: Int,
        val type: String,
        val title: String?,
        val fold: CalloutFold,
        val body: String,
        val bodyStart: Int,
    ) : Region
}

internal object MarkdownContainers {
    fun regions(text: String): List<Region> {
        if (!text.contains(DETAILS_WORD, ignoreCase = true) && !text.contains(CALLOUT_OPENING)) return listOf(Region.Plain(0, text.length))
        val regions = mutableListOf<Region>()
        var plainStart = 0
        var fence: String? = null
        var afterQuote = false
        var lineStart = 0
        while (lineStart < text.length) {
            val lineEnd = lineEnd(text, lineStart)
            val line = text.substring(lineStart, lineEnd).trimEnd('\r')
            val insideFence = fence != null
            fence = MarkdownFences.next(fence, line)
            val region = if (insideFence || fence != null) null else detailsAt(text, lineStart, line) ?: calloutAt(text, lineStart, line, afterQuote)
            if (region == null) {
                afterQuote = QUOTE_PREFIX.containsMatchIn(line)
                lineStart = lineEnd + 1
                continue
            }
            if (lineStart > plainStart) regions += Region.Plain(plainStart, lineStart)
            regions += region
            plainStart = region.end
            afterQuote = false
            lineStart = lineEnd(text, region.end) + 1
        }
        if (plainStart < text.length) regions += Region.Plain(plainStart, text.length)
        return regions
    }

    fun plainTitle(raw: String): String? = HTML_TAG.replace(raw, "").replace(BOLD_MARK, "").replace(WHITESPACE, " ").trim().ifEmpty { null }

    private fun detailsAt(text: String, lineStart: Int, line: String): Region.Details? {
        val opening = DETAILS_OPENING.find(line) ?: return null
        val openTagEnd = lineStart + opening.range.last + 1
        val close = closingTag(text, lineStart, openTagEnd)
        val bodyEnd = close?.first ?: text.length
        val inner = text.substring(openTagEnd, bodyEnd)
        val summary = SUMMARY_OPENING.find(inner)
        val summaryText: String?
        val bodyStart: Int
        if (summary == null) {
            summaryText = null
            bodyStart = openTagEnd
        } else {
            val summaryStart = summary.range.last + 1
            val summaryClose = SUMMARY_CLOSING.find(inner, summaryStart)
            summaryText = plainTitle(inner.substring(summaryStart, summaryClose?.range?.first ?: inner.length))
            bodyStart = if (summaryClose == null) bodyEnd else openTagEnd + summaryClose.range.last + 1
        }
        return Region.Details(
            start = lineStart,
            end = close?.let { it.last + 1 } ?: text.length,
            summary = summaryText,
            isOpen = OPEN_ATTRIBUTE.containsMatchIn(opening.value),
            bodyStart = bodyStart,
            bodyEnd = bodyEnd,
        )
    }

    private fun closingTag(text: String, openingLineStart: Int, openTagEnd: Int): IntRange? {
        var depth = 1
        var fence: String? = null
        var lineStart = openingLineStart
        while (lineStart < text.length) {
            val lineEnd = lineEnd(text, lineStart)
            val line = text.substring(lineStart, lineEnd).trimEnd('\r')
            val insideFence = fence != null
            fence = MarkdownFences.next(fence, line)
            if (!insideFence && fence == null) {
                val from = maxOf(openTagEnd - lineStart, 0)
                val tags = (DETAILS_OPEN.findAll(line, from) + DETAILS_CLOSE.findAll(line, from)).sortedBy { it.range.first }
                for (tag in tags) {
                    depth += if (tag.value.startsWith("</")) -1 else 1
                    if (depth == 0) return (lineStart + tag.range.first)..(lineStart + tag.range.last)
                }
            }
            lineStart = lineEnd + 1
        }
        return null
    }

    private fun calloutAt(text: String, lineStart: Int, line: String, afterQuote: Boolean): Region.Callout? {
        if (afterQuote) return null
        val prefix = QUOTE_PREFIX.find(line) ?: return null
        val header = CALLOUT_HEADER.matchEntire(line.substring(prefix.value.length).trimEnd()) ?: return null
        val firstLineEnd = lineEnd(text, lineStart)
        val body = mutableListOf<String>()
        var end = firstLineEnd
        while (end + 1 < text.length) {
            val nextEnd = lineEnd(text, end + 1)
            val nextLine = text.substring(end + 1, nextEnd).trimEnd('\r')
            val nextPrefix = QUOTE_PREFIX.find(nextLine) ?: break
            body += nextLine.substring(nextPrefix.value.length)
            end = nextEnd
        }
        return Region.Callout(
            start = lineStart,
            end = end,
            type = header.groupValues[1].lowercase(),
            title = plainTitle(header.groupValues[3]),
            fold = when (header.groupValues[2]) {
                "+" -> CalloutFold.OPEN
                "-" -> CalloutFold.CLOSED
                else -> CalloutFold.NONE
            },
            body = body.joinToString("\n"),
            bodyStart = minOf(firstLineEnd + 1, text.length),
        )
    }

    private fun lineEnd(text: String, from: Int): Int = text.indexOf('\n', from).let { if (it < 0) text.length else it }

    private const val DETAILS_WORD = "<details"
    private const val CALLOUT_OPENING = "[!"
    private val DETAILS_OPENING = Regex("""^ {0,3}<details(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val DETAILS_OPEN = Regex("""<details(?=[\s>])""", RegexOption.IGNORE_CASE)
    private val DETAILS_CLOSE = Regex("""</details\s*>""", RegexOption.IGNORE_CASE)
    private val SUMMARY_OPENING = Regex("""^\s*<summary(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val SUMMARY_CLOSING = Regex("""</summary\s*>""", RegexOption.IGNORE_CASE)
    private val OPEN_ATTRIBUTE = Regex("""\sopen(\s|=|>)""", RegexOption.IGNORE_CASE)
    private val QUOTE_PREFIX = Regex("""^ {0,3}> ?""")
    private val CALLOUT_HEADER = Regex("""\[!([A-Za-z][A-Za-z0-9_-]*)]([+-])?(?:[ \t]+(.*))?""")
    private val HTML_TAG = Regex("""<[^>]*>""")
    private val BOLD_MARK = Regex("""\*\*|__""")
    private val WHITESPACE = Regex("""\s+""")
}
