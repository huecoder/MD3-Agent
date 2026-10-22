package dev.huecoder.md3agent.feature.chat

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import dev.huecoder.md3agent.ui.markdown.InlineSyntax
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.LeafASTNode

@Stable
class InlineMarks(
    private val highlight: SpanStyle,
    private val footnoteReference: SpanStyle,
    private val onFootnote: (Int) -> Unit,
) {
    fun annotator(base: MarkdownAnnotator): MarkdownAnnotator {
        val open = OpenHighlight()
        return markdownAnnotator(base.config) { content, node ->
            if (node.type == MarkdownTokenTypes.TEXT && hasMarker(content, node.startOffset, node.endOffset)) {
                appendMarked(base, open, content, node.startOffset, node.endOffset)
                true
            } else {
                base.annotate?.invoke(this, content, node) == true
            }
        }
    }

    private fun hasMarker(content: String, from: Int, to: Int): Boolean = (from until to).any { InlineSyntax.isMarker(content[it]) }

    private fun AnnotatedString.Builder.appendMarked(base: MarkdownAnnotator, open: OpenHighlight, content: String, from: Int, to: Int) {
        var pieceStart = from
        for (index in from until to) {
            val char = content[index]
            if (!InlineSyntax.isMarker(char)) continue
            appendPiece(base, content, pieceStart, index)
            appendMarker(open, char)
            pieceStart = index + 1
        }
        appendPiece(base, content, pieceStart, to)
    }

    private fun AnnotatedString.Builder.appendPiece(base: MarkdownAnnotator, content: String, from: Int, to: Int) {
        if (from >= to) return
        val piece = LeafASTNode(MarkdownTokenTypes.TEXT, from, to)
        if (base.annotate?.invoke(this, content, piece) != true) append(piece.getUnescapedTextInNode(content))
    }

    private fun AnnotatedString.Builder.appendMarker(open: OpenHighlight, marker: Char) {
        when (marker) {
            InlineSyntax.HIGHLIGHT_OPEN -> open.begin(this, length)
            InlineSyntax.HIGHLIGHT_CLOSE -> open.end(this)?.let { start -> if (start < length) addStyle(highlight, start, length) }
            else -> InlineSyntax.footnoteNumber(marker)?.let { number -> appendFootnoteReference(number) }
        }
    }

    private fun AnnotatedString.Builder.appendFootnoteReference(number: Int) {
        val link = LinkAnnotation.Clickable(
            tag = "$FOOTNOTE_TAG$number",
            styles = TextLinkStyles(footnoteReference),
            linkInteractionListener = { onFootnote(number) },
        )
        withLink(link) { append(number.toString()) }
    }

    private class OpenHighlight {
        private var builder: AnnotatedString.Builder? = null
        private var start = 0

        fun begin(target: AnnotatedString.Builder, at: Int) {
            builder = target
            start = at
        }

        fun end(target: AnnotatedString.Builder): Int? {
            val matches = builder === target
            builder = null
            return start.takeIf { matches }
        }
    }

    private companion object {
        const val FOOTNOTE_TAG = "footnote-"
    }
}
