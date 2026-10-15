package dev.huecoder.md3agent.system.control

object PhoneSay {
    const val MAX_CHARS = 60

    private val links = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val lineMarkers = Regex("""(?m)^\s*(#{1,6}|>|[-+*])\s+""")
    private val emphasis = Regex("""[*`~]+|(?<![\p{L}\p{N}])_+|_+(?![\p{L}\p{N}])""")
    private val whitespace = Regex("""\s+""")
    private const val QUOTES = "\"'“”‘’«»"

    fun clean(raw: String?): String? {
        val plain = raw.orEmpty()
            .replace(links, "$1")
            .replace(lineMarkers, "")
            .replace(emphasis, "")
            .replace(whitespace, " ")
            .trim()
            .trim { it in QUOTES }
            .trim()
        if (plain.isEmpty()) return null
        return if (plain.length <= MAX_CHARS) plain else shortened(plain)
    }

    private fun shortened(text: String): String {
        val room = text.take(MAX_CHARS - 1)
        val cut = room.lastIndexOf(' ').takeIf { it >= MAX_CHARS / 2 } ?: room.length
        return room.take(cut).trimEnd(',', '.', ';', ':', '-', ' ') + "…"
    }
}
