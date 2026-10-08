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
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.rememberMarkdownState

@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    Markdown(
        markdownState = rememberMarkdownState(text, retainState = true),
        modifier = modifier.fillMaxWidth(),
        colors = appMarkdownColors(),
        typography = appMarkdownTypography(),
        components = appMarkdownComponents,
        imageTransformer = Coil3ImageTransformerImpl,
    )
}

@Composable
fun MarkdownContent(parsed: State, annotator: MarkdownAnnotator, modifier: Modifier = Modifier) {
    Markdown(
        state = parsed,
        modifier = modifier.fillMaxWidth(),
        colors = appMarkdownColors(),
        typography = appMarkdownTypography(),
        annotator = annotator,
        components = appMarkdownComponents,
        imageTransformer = Coil3ImageTransformerImpl,
    )
}

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
