package dev.huecoder.md3agent.feature.home

object ChatPreview {
    private val fencedCode = Regex("```[\\s\\S]*?(```|$)")
    private val image = Regex("!\\[([^]]*)]\\([^)]*\\)")
    private val link = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val linePrefix = Regex("(?m)^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+\\[[ xX]]\\s+|[-*+]\\s+|\\d+[.)]\\s+)")
    private val emphasis = Regex("(\\*{1,3}|_{2,3}|~~|`+|\\|\\|)")
    private val whitespace = Regex("\\s+")

    fun of(markdown: String): String = markdown
        .replace(fencedCode, " ")
        .replace(image, "$1")
        .replace(link, "$1")
        .replace(linePrefix, "")
        .replace(emphasis, "")
        .replace(whitespace, " ")
        .trim()
}
