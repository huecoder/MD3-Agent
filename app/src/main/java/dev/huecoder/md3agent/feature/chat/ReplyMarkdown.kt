package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownInlineContent
import dev.huecoder.md3agent.ui.markdown.Footnote
import dev.huecoder.md3agent.ui.markdown.InlineSyntax
import dev.huecoder.md3agent.ui.math.MathContent
import dev.huecoder.md3agent.ui.math.rememberMathContent
import kotlinx.coroutines.launch

@Stable
class FootnoteTargets {
    private val requesters = mutableMapOf<Int, BringIntoViewRequester>()

    fun requester(number: Int): BringIntoViewRequester = requesters.getOrPut(number) { BringIntoViewRequester() }
}

@Stable
class ReplyMarkdown internal constructor(
    val math: MathContent,
    val markdown: String,
    val footnotes: List<Footnote>,
    val targets: FootnoteTargets,
    private val marks: InlineMarks,
) {
    val inlineContent: MarkdownInlineContent get() = math.inlineContent

    fun annotator(base: MarkdownAnnotator): MarkdownAnnotator = marks.annotator(math.annotator(base))
}

@Composable
fun rememberReplyMarkdown(text: String): ReplyMarkdown {
    val math = rememberMathContent(text)
    val prepared = remember(math.markdown) { InlineSyntax.prepare(math.markdown) }
    val targets = remember { FootnoteTargets() }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val marks = remember(colors.primary) {
        InlineMarks(
            highlight = SpanStyle(color = colors.primary, background = colors.primary.copy(alpha = HIGHLIGHT_BACKGROUND_ALPHA)),
            footnoteReference = SpanStyle(
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = FOOTNOTE_SIZE,
                baselineShift = BaselineShift.Superscript,
            ),
            onFootnote = { number -> scope.launch { targets.requester(number).bringIntoView() } },
        )
    }
    return remember(math, prepared, marks) { ReplyMarkdown(math, prepared.markdown, prepared.footnotes, targets, marks) }
}

private const val HIGHLIGHT_BACKGROUND_ALPHA = 0.18f
private val FOOTNOTE_SIZE = 0.75f.em
