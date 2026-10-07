package dev.huecoder.md3assistant.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState

@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val state = rememberMarkdownState(text, retainState = true)
    val typography = MaterialTheme.typography
    Markdown(
        markdownState = state,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        typography = markdownTypography(
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
        ),
        components = markdownComponents(codeFence = highlightedCodeFence, codeBlock = highlightedCodeBlock),
    )
}
