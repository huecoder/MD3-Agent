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
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownImage
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.huecoder.md3agent.ui.math.LocalMathContent
import dev.huecoder.md3agent.ui.math.MathContent
import dev.huecoder.md3agent.ui.math.rememberMathContent
import dev.huecoder.md3agent.ui.pictures.MarkdownBlock
import dev.huecoder.md3agent.ui.pictures.MarkdownBlocks
import dev.huecoder.md3agent.ui.pictures.PictureSyntax
import org.intellij.markdown.ast.getTextInNode

@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val math = rememberMathContent(text)
    val blocks = remember(math.markdown) { MarkdownBlocks.split(math.markdown) }
    MarkdownBlocksContent(blocks, math, modifier) { block ->
        Markdown(
            markdownState = rememberMarkdownState(block.markdown, retainState = true),
            modifier = Modifier.fillMaxWidth(),
            colors = appMarkdownColors(),
            typography = appMarkdownTypography(),
            annotator = math.annotator(PlainAnnotator),
            inlineContent = math.inlineContent,
            components = appMarkdownComponents,
            imageTransformer = Coil3ImageTransformerImpl,
        )
    }
}

@Composable
fun MarkdownBlocksContent(
    blocks: List<MarkdownBlock>,
    math: MathContent,
    modifier: Modifier = Modifier,
    text: @Composable (MarkdownBlock.Text) -> Unit,
) {
    CompositionLocalProvider(LocalMathContent provides math) {
        Column(modifier.fillMaxWidth().then(math.measure), verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
            blocks.forEachIndexed { index, block ->
                key(index) {
                    when (block) {
                        is MarkdownBlock.Text -> text(block)
                        is MarkdownBlock.Pictures -> PictureGroupView(block)
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownContent(parsed: State, annotator: MarkdownAnnotator, math: MathContent, modifier: Modifier = Modifier) {
    Markdown(
        state = parsed,
        modifier = modifier.fillMaxWidth(),
        colors = appMarkdownColors(),
        typography = appMarkdownTypography(),
        annotator = math.annotator(annotator),
        inlineContent = math.inlineContent,
        components = appMarkdownComponents,
        imageTransformer = Coil3ImageTransformerImpl,
    )
}

val PlainAnnotator: MarkdownAnnotator = markdownAnnotator()

private val appMarkdownComponents: MarkdownComponents = markdownComponents(
    codeFence = highlightedCodeFence,
    codeBlock = highlightedCodeBlock,
    image = { model -> InTextPicture(model) },
    paragraph = { model -> FormulaParagraph(model) },
    table = { model -> ChatTable(model) },
    checkbox = { model -> TaskCheckbox(model) },
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
private fun appMarkdownColors(): MarkdownColors = markdownColor(
    text = MaterialTheme.colorScheme.onSurface,
    codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
    inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
)

@Composable
private fun appMarkdownTypography(): MarkdownTypography {
    val typography = MaterialTheme.typography
    return markdownTypography(
        h1 = typography.headlineSmall,
        h2 = typography.titleLarge,
        h3 = typography.titleMedium,
        h4 = typography.titleSmall,
        h5 = typography.labelLarge,
        h6 = typography.labelMedium,
        text = typography.bodyLarge,
        paragraph = typography.bodyLarge,
        bullet = typography.bodyLarge,
        list = typography.bodyLarge,
        ordered = typography.bodyLarge,
        quote = typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        table = typography.bodyMedium,
        textLink = TextLinkStyles(
            SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
        ),
    )
}

private const val CHECKED_MARK = "[x]"
private val BLOCK_GAP = 8.dp
private val CHECKBOX_GAP = 4.dp
