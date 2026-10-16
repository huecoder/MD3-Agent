package dev.huecoder.md3agent.ui.pictures

sealed interface MarkdownBlock {
    val start: Int

    data class Text(val markdown: String, override val start: Int) : MarkdownBlock

    data class Pictures(val pictures: List<Picture>, override val start: Int) : MarkdownBlock {
        val options: PictureOptions = PictureOptions(
            align = pictures.firstNotNullOfOrNull { it.options.align },
            widthShare = pictures.firstNotNullOfOrNull { it.options.widthShare },
            grouping = pictures.firstNotNullOfOrNull { it.options.grouping },
        )
    }
}

object MarkdownBlocks {
    fun split(text: String): List<MarkdownBlock> {
        if (!text.contains(IMAGE_OPENING)) return listOf(MarkdownBlock.Text(text, 0))
        val blocks = mutableListOf<MarkdownBlock>()
        val group = mutableListOf<Picture>()
        var textStart = 0
        var groupStart = 0
        var fence: String? = null
        var lineStart = 0
        while (lineStart < text.length) {
            val newline = text.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) text.length else newline
            val line = text.substring(lineStart, lineEnd).trimEnd('\r')
            fence = nextFence(fence, line)
            val pictures = if (fence == null && !isFenceLine(line)) PictureSyntax.line(line) else null
            if (pictures != null) {
                if (group.isEmpty()) {
                    addText(blocks, text, textStart, lineStart)
                    groupStart = lineStart
                }
                group += pictures
            } else if (group.isNotEmpty()) {
                blocks += MarkdownBlock.Pictures(group.toList(), groupStart)
                group.clear()
                textStart = lineStart
            }
            lineStart = lineEnd + 1
        }
        if (group.isNotEmpty()) blocks += MarkdownBlock.Pictures(group.toList(), groupStart) else addText(blocks, text, textStart, text.length)
        return blocks.ifEmpty { listOf(MarkdownBlock.Text(text, 0)) }
    }

    private fun addText(blocks: MutableList<MarkdownBlock>, text: String, from: Int, to: Int) {
        if (to > from && text.substring(from, to).isNotBlank()) blocks += MarkdownBlock.Text(text.substring(from, to), from)
    }

    private fun nextFence(open: String?, line: String): String? {
        val marker = fenceMarker(line) ?: return open
        return when {
            open == null -> marker
            marker.startsWith(open.take(1)) && marker.length >= open.length -> null
            else -> open
        }
    }

    private fun isFenceLine(line: String): Boolean = fenceMarker(line) != null

    private fun fenceMarker(line: String): String? {
        val trimmed = line.trimStart()
        if (line.length - trimmed.length > MAX_FENCE_INDENT) return null
        val char = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val length = trimmed.takeWhile { it == char }.length
        return char.toString().repeat(length).takeIf { length >= MIN_FENCE }
    }

    private const val IMAGE_OPENING = "!["
    private const val MAX_FENCE_INDENT = 3
    private const val MIN_FENCE = 3
}
