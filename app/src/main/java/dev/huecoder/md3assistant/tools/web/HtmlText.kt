package dev.huecoder.md3assistant.tools.web

object HtmlText {
    private val invisibleBlocks = Regex(
        "<(script|style|noscript|svg|template|head|nav|footer|form)\\b[^>]*>[\\s\\S]*?</\\1\\s*>",
        RegexOption.IGNORE_CASE,
    )
    private val comments = Regex("<!--[\\s\\S]*?-->")
    private val blockBoundaries = Regex(
        "<(br|/p|/div|/h[1-6]|/tr|/section|/article|/blockquote|/pre)\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val listItems = Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val tags = Regex("<[^>]+>")
    private val titleTag = Regex("<title\\b[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")
    private val namedEntity = Regex("&([a-zA-Z]+);")
    private val spaces = Regex("[ \\t\\u00A0]+")
    private val blankLines = Regex("\\n\\s*\\n+")

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "laquo" to "«", "raquo" to "»", "mdash" to "—", "ndash" to "–", "hellip" to "…", "copy" to "©",
    )

    fun inline(html: String): String = decodeEntities(html.replace(tags, "")).replace(Regex("\\s+"), " ").trim()

    fun document(html: String): String {
        val withoutNoise = html.replace(comments, "").replace(invisibleBlocks, " ")
        val withBreaks = withoutNoise.replace(listItems, "\n• ").replace(blockBoundaries, "\n")
        return decodeEntities(withBreaks.replace(tags, " "))
            .lines()
            .joinToString("\n") { it.replace(spaces, " ").trim() }
            .replace(blankLines, "\n\n")
            .trim()
    }

    fun title(html: String): String? = titleTag.find(html)?.groupValues?.get(1)?.let(::inline)?.takeIf { it.isNotEmpty() }

    fun decodeEntities(text: String): String = text
        .replace(numericEntity) { match ->
            val radix = if (match.groupValues[1].isEmpty()) DECIMAL else HEX
            match.groupValues[2].toIntOrNull(radix)?.let { codePoint ->
                runCatching { String(Character.toChars(codePoint)) }.getOrNull()
            } ?: match.value
        }
        .replace(namedEntity) { match -> named[match.groupValues[1].lowercase()] ?: match.value }

    private const val DECIMAL = 10
    private const val HEX = 16
}
