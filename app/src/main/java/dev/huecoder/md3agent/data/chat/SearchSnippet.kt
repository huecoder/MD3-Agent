package dev.huecoder.md3agent.data.chat

data class SearchSnippet(val text: String, val match: IntRange?) {
    companion object {
        private const val ELLIPSIS = "…"
        private val WHITESPACE = Regex("\\s+")

        fun around(content: String, query: String, width: Int): SearchSnippet {
            val flat = content.replace(WHITESPACE, " ").trim()
            val needle = query.trim()
            val at = if (needle.isEmpty()) -1 else flat.indexOf(needle, ignoreCase = true)
            if (at < 0) return SearchSnippet(flat.take(width).let { if (flat.length > width) it.trimEnd() + ELLIPSIS else it }, null)
            val lead = ((width - needle.length) / 2).coerceAtLeast(0)
            var start = (at - lead).coerceAtLeast(0)
            var end = (start + width).coerceAtMost(flat.length)
            start = (end - width).coerceAtLeast(0)
            if (start > 0) start = flat.indexOf(' ', start).takeIf { it in start until at }?.plus(1) ?: start
            if (end < flat.length) end = flat.lastIndexOf(' ', end).takeIf { it > at + needle.length } ?: end
            val prefix = if (start > 0) ELLIPSIS else ""
            val suffix = if (end < flat.length) ELLIPSIS else ""
            val body = flat.substring(start, end)
            val matchStart = prefix.length + at - start
            return SearchSnippet(prefix + body + suffix, matchStart until matchStart + needle.length)
        }
    }
}
