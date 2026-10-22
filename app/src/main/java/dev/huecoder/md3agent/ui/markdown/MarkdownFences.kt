package dev.huecoder.md3agent.ui.markdown

internal object MarkdownFences {
    fun next(open: String?, line: String): String? {
        val marker = marker(line) ?: return open
        return when {
            open == null -> marker
            marker.startsWith(open.take(1)) && marker.length >= open.length -> null
            else -> open
        }
    }

    fun isFenceLine(line: String): Boolean = marker(line) != null

    private fun marker(line: String): String? {
        val trimmed = line.trimStart()
        if (line.length - trimmed.length > MAX_INDENT) return null
        val char = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val length = trimmed.takeWhile { it == char }.length
        return char.toString().repeat(length).takeIf { length >= MIN_LENGTH }
    }

    private const val MAX_INDENT = 3
    private const val MIN_LENGTH = 3
}
