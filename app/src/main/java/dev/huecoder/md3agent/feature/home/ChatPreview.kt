package dev.huecoder.md3agent.feature.home

object ChatPreview {
    private val fencedCode = Regex("```[\\s\\S]*?(```|$)")
    private val image = Regex("!\\[([^]]*)]\\([^)]*\\)")
    private val link = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val linePrefix = Regex("(?m)^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+\\[[ xX]]\\s+|[-*+]\\s+|\\d+[.)]\\s+)")
    private val emphasis = Regex("(\\*{1,3}|_{2,3}|~~|`+|\\|\\|)")
    private val highlight = Regex("==(?=\\S)|(?<=\\S)==")
    private val footnoteMark = Regex("\\[\\^[^]]+]:?")
    private val calloutMarker = Regex("\\[![A-Za-z][\\w-]*][+-]?")
    private val detailsTag = Regex("</?(details|summary)[^>]*>", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")

    fun of(markdown: String): String = markdown
        .replace(fencedCode, " ")
        .replace(image, "$1")
        .replace(detailsTag, " ")
        .replace(footnoteMark, "")
        .replace(calloutMarker, "")
        .replace(link, "$1")
        .replace(linePrefix, "")
        .replace(emphasis, "")
        .replace(highlight, "")
        .replace(whitespace, " ")
        .trim()
}
