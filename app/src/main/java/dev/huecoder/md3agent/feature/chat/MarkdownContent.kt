package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownImage
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.RoundedCornerSpanPainter
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownDimens
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.huecoder.md3agent.ui.markdown.MarkdownBlock
import dev.huecoder.md3agent.ui.markdown.MarkdownBlocks
import dev.huecoder.md3agent.ui.math.LocalMathContent
import dev.huecoder.md3agent.ui.pictures.PictureSyntax
import org.intellij.markdown.ast.getTextInNode

@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val reply = rememberReplyMarkdown(text)
    val blocks = remember(reply.markdown) { MarkdownBlocks.split(reply.markdown) }
    MarkdownBlocksContent(blocks, reply, modifier) { block ->
        Markdown(
            markdownState = rememberMarkdownState(block.markdown, retainState = true),
            modifier = Modifier.fillMaxWidth(),
            colors = appMarkdownColors(),
            typography = appMarkdownTypography(),
            dimens = appMarkdownDimens(),
            extendedSpans = markdownExtendedSpans(roundedSpans),
            annotator = reply.annotator(PlainAnnotator),
            inlineContent = reply.inlineContent,
            components = appMarkdownComponents,
            imageTransformer = Coil3ImageTransformerImpl,
        )
    }
}

@Composable
fun MarkdownBlocksContent(
    blocks: List<MarkdownBlock>,
    reply: ReplyMarkdown,
    modifier: Modifier = Modifier,
    text: @Composable (MarkdownBlock.Text) -> Unit,
) {
    CompositionLocalProvider(LocalMathContent provides reply.math) {
        Column(modifier.fillMaxWidth().then(reply.math.measure), verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
            Blocks(blocks, text)
            if (reply.footnotes.isNotEmpty()) FootnotesView(reply)
        }
    }
}

@Composable
private fun Blocks(blocks: List<MarkdownBlock>, text: @Composable (MarkdownBlock.Text) -> Unit) {
    blocks.forEachIndexed { index, block ->
        key(index) {
            when (block) {
                is MarkdownBlock.Text -> text(block)
                is MarkdownBlock.Pictures -> PictureGroupView(block)
                is MarkdownBlock.Callout -> CalloutView(block) { body -> NestedBlocks(body, text) }
                is MarkdownBlock.Details -> DetailsView(block) { body -> NestedBlocks(body, text) }
            }
        }
    }
}

@Composable
private fun NestedBlocks(blocks: List<MarkdownBlock>, text: @Composable (MarkdownBlock.Text) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) { Blocks(blocks, text) }
}

@Composable
fun MarkdownContent(parsed: State, annotator: MarkdownAnnotator, reply: ReplyMarkdown, modifier: Modifier = Modifier, compact: Boolean = false) {
    Markdown(
        state = parsed,
        modifier = modifier.fillMaxWidth(),
        colors = appMarkdownColors(compact),
        typography = appMarkdownTypography(compact),
        dimens = appMarkdownDimens(),
        extendedSpans = markdownExtendedSpans(roundedSpans),
        annotator = reply.annotator(annotator),
        inlineContent = reply.inlineContent,
        components = appMarkdownComponents,
        imageTransformer = Coil3ImageTransformerImpl,
    )
}

val PlainAnnotator: MarkdownAnnotator = markdownAnnotator()

private val roundedSpans: @Composable () -> ExtendedSpans = {
    remember {
        ExtendedSpans(
            RoundedCornerSpanPainter(
                cornerRadius = INLINE_CORNER,
                padding = RoundedCornerSpanPainter.TextPaddingValues(horizontal = INLINE_PADDING, vertical = INLINE_PADDING),
            ),
        )
    }
}

private val appMarkdownComponents: MarkdownComponents = markdownComponents(
    codeFence = highlightedCodeFence,
    codeBlock = highlightedCodeBlock,
    image = { model -> InTextPicture(model) },
    paragraph = { model -> FormulaParagraph(model) },
    table = { model -> ChatTable(model) },
    checkbox = { model -> TaskCheckbox(model) },
    blockQuote = { model -> QuoteBlock(model) },
    horizontalRule = { RoundedRule() },
)

@Composable
private fun TaskCheckbox(model: MarkdownComponentModel) {
    val checked = model.node.getTextInNode(model.content).contains(CHECKED_MARK, ignoreCase = true)
    val lineHeight = model.typography.text.lineHeight
    val lineModifier = if (lineHeight.isSp) Modifier.height(with(LocalDensity.current) { lineHeight.toDp() }) else Modifier
    Box(lineModifier, contentAlignment = Alignment.CenterStart) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier
                .padding(end = CHECKBOX_GAP)
                .semantics {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(checked)
                },
        )
    }
}

@Composable
private fun FormulaParagraph(model: MarkdownComponentModel) {
    val style = model.typography.paragraph
    val math = LocalMathContent.current
    val paragraph = model.content.substring(model.node.startOffset, model.node.endOffset)
    val fitted = if (math == null) style else style.copy(lineHeight = math.lineHeightFor(paragraph, style.lineHeight))
    MarkdownParagraph(model.content, model.node, style = fitted)
}

@Composable
private fun InTextPicture(model: MarkdownComponentModel) {
    val picture = PictureSyntax.single(model.content.substring(model.node.startOffset, model.node.endOffset))
    if (picture != null) SinglePictureView(picture) else MarkdownImage(model.content, model.node)
}

@Composable
private fun appMarkdownColors(compact: Boolean = false): MarkdownColors {
    val colors = MaterialTheme.colorScheme
    return markdownColor(
        text = if (compact) colors.onSurfaceVariant else colors.onSurface,
        codeBackground = colors.surfaceContainerHighest,
        inlineCodeBackground = colors.onSurface.copy(alpha = INLINE_CODE_ALPHA),
    )
}

@Composable
private fun appMarkdownDimens(): MarkdownDimens = markdownDimens(codeBackgroundCornerSize = MARKDOWN_BLOCK_CORNER)

@Composable
private fun appMarkdownTypography(compact: Boolean = false): MarkdownTypography {
    val typography = MaterialTheme.typography
    val body = if (compact) typography.bodyMedium else typography.bodyLarge
    return markdownTypography(
        h1 = typography.headlineSmall,
        h2 = typography.titleLarge,
        h3 = typography.titleMedium,
        h4 = typography.titleSmall,
        h5 = typography.labelLarge,
        h6 = typography.labelMedium,
        text = body,
        paragraph = body,
        bullet = body,
        list = body,
        ordered = body,
        quote = body.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        table = typography.bodyMedium,
        textLink = TextLinkStyles(
            SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
        ),
    )
}

private const val CHECKED_MARK = "[x]"
private const val INLINE_CODE_ALPHA = 0.12f
private val INLINE_CORNER = 8.sp
private val INLINE_PADDING = 1.sp
private val BLOCK_GAP = 8.dp
private val CHECKBOX_GAP = 4.dp
