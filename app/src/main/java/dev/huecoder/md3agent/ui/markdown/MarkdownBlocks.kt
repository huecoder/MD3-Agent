package dev.huecoder.md3agent.ui.markdown

import dev.huecoder.md3agent.ui.pictures.Picture
import dev.huecoder.md3agent.ui.pictures.PictureOptions
import dev.huecoder.md3agent.ui.pictures.PictureSyntax

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

    data class Callout(
        val type: String,
        val title: String?,
        val fold: CalloutFold,
        val body: List<MarkdownBlock>,
        override val start: Int,
    ) : MarkdownBlock {
        val kind: CalloutKind? get() = CalloutKind.of(type)
    }

    data class Details(
        val summary: String?,
        val isOpen: Boolean,
        val body: List<MarkdownBlock>,
        override val start: Int,
    ) : MarkdownBlock
}

object MarkdownBlocks {
    fun split(text: String): List<MarkdownBlock> = split(text, 0).ifEmpty { listOf(MarkdownBlock.Text(text, 0)) }

    private fun split(text: String, base: Int): List<MarkdownBlock> = MarkdownContainers.regions(text).flatMap { region ->
        when (region) {
            is Region.Plain -> splitPictures(text.substring(region.start, region.end), base + region.start)
            is Region.Details -> listOf(
                MarkdownBlock.Details(
                    summary = region.summary,
                    isOpen = region.isOpen,
                    body = split(text.substring(region.bodyStart, region.bodyEnd), base + region.bodyStart),
                    start = base + region.start,
                ),
            )
            is Region.Callout -> listOf(
                MarkdownBlock.Callout(
                    type = region.type,
                    title = region.title,
                    fold = region.fold,
                    body = split(region.body, base + region.bodyStart),
                    start = base + region.start,
                ),
            )
        }
    }

    private fun splitPictures(text: String, base: Int): List<MarkdownBlock> {
        if (!text.contains(IMAGE_OPENING)) return if (text.isBlank()) emptyList() else listOf(MarkdownBlock.Text(text, base))
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
            fence = MarkdownFences.next(fence, line)
            val pictures = if (fence == null && !MarkdownFences.isFenceLine(line)) PictureSyntax.line(line) else null
            if (pictures != null) {
                if (group.isEmpty()) {
                    addText(blocks, text, textStart, lineStart, base)
                    groupStart = lineStart
                }
                group += pictures
            } else if (group.isNotEmpty()) {
                blocks += MarkdownBlock.Pictures(group.toList(), base + groupStart)
                group.clear()
                textStart = lineStart
            }
            lineStart = lineEnd + 1
        }
        if (group.isNotEmpty()) blocks += MarkdownBlock.Pictures(group.toList(), base + groupStart) else addText(blocks, text, textStart, text.length, base)
        return blocks
    }

    private fun addText(blocks: MutableList<MarkdownBlock>, text: String, from: Int, to: Int, base: Int) {
        if (to > from && text.substring(from, to).isNotBlank()) blocks += MarkdownBlock.Text(text.substring(from, to), base + from)
    }

    private const val IMAGE_OPENING = "!["
}
