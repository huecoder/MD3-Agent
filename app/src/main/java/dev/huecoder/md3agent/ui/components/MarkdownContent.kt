package dev.huecoder.md3agent.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
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
import dev.huecoder.md3agent.ui.math.MathContent
import dev.huecoder.md3agent.ui.math.rememberMathContent

@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val math = rememberMathContent(text)
    Markdown(
        markdownState = rememberMarkdownState(math.markdown, retainState = true),
        modifier = modifier.fillMaxWidth().then(math.measure),
        colors = appMarkdownColors(),
        typography = appMarkdownTypography(),
        annotator = math.annotator(PlainAnnotator),
        inlineContent = math.inlineContent,
        components = appMarkdownComponents,
        imageTransformer = Coil3ImageTransformerImpl,
    )
}

@Composable
fun MarkdownContent(parsed: State, annotator: MarkdownAnnotator, math: MathContent, modifier: Modifier = Modifier) {
    Markdown(
        state = parsed,
        modifier = modifier.fillMaxWidth().then(math.measure),
        colors = appMarkdownColors(),
        typography = appMarkdownTypography(),
        annotator = math.annotator(annotator),
        inlineContent = math.inlineContent,
        components = appMarkdownComponents,
        imageTransformer = Coil3ImageTransformerImpl,
    )
}

val PlainAnnotator: MarkdownAnnotator = markdownAnnotator()

private val appMarkdownComponents: MarkdownComponents = markdownComponents(codeFence = highlightedCodeFence, codeBlock = highlightedCodeBlock)

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
        textLink = TextLinkStyles(
            SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
        ),
    )
}
