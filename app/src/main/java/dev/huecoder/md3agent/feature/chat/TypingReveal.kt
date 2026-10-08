package dev.huecoder.md3agent.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.parseMarkdown
import dev.huecoder.md3agent.data.settings.TypingMode
import dev.huecoder.md3agent.data.settings.TypingStyle
import dev.huecoder.md3agent.ui.components.MarkdownContent
import kotlinx.coroutines.flow.first
import org.intellij.markdown.MarkdownTokenTypes
import kotlin.math.ceil

@Composable
fun ReplyText(text: String, isStreaming: Boolean, style: TypingStyle, modifier: Modifier = Modifier) {
    val startedLive = remember { isStreaming }
    if (!style.isAnimated || !startedLive) {
        MarkdownContent(text, modifier)
        return
    }
    val tail = style.fadeTailChars
    val frontier = rememberRevealFrontier(text, style.charsPerSecond, tail)
    val shown = text.take(ceil(frontier).toInt().coerceIn(0, text.length))
    val parsed = remember(shown) { parseMarkdown(shown) }
    val annotator = if (tail == 0) PlainAnnotator else fadeAnnotator(frontier, tail, MaterialTheme.colorScheme.onSurface)
    MarkdownContent(parsed, annotator, modifier)
}

@Composable
private fun rememberRevealFrontier(text: String, charsPerSecond: Int, tail: Int): Float {
    val frontier = remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(text)
    LaunchedEffect(charsPerSecond, tail) {
        while (true) {
            if (frontier.floatValue >= latest.length + tail) {
                snapshotFlow { latest.length + tail }.first { it > frontier.floatValue }
            }
            advance(frontier, { latest.length }, charsPerSecond, tail)
        }
    }
    return frontier.floatValue
}

private suspend fun advance(frontier: MutableFloatState, length: () -> Int, charsPerSecond: Int, tail: Int) {
    var previous = withFrameNanos { it }
    while (frontier.floatValue < length() + tail) {
        withFrameNanos { now ->
            val seconds = (now - previous) / NANOS_PER_SECOND
            previous = now
            val backlog = length() - frontier.floatValue
            val allowedLag = charsPerSecond * MAX_LAG_SECONDS
            val speed = charsPerSecond + (backlog - allowedLag).coerceAtLeast(0f) * CATCH_UP_PER_SECOND
            frontier.floatValue = (frontier.floatValue + speed * seconds).coerceAtMost((length() + tail).toFloat())
        }
    }
}

private fun fadeAnnotator(frontier: Float, tail: Int, color: Color): MarkdownAnnotator = markdownAnnotator { content, node ->
    val fadeStart = frontier - tail
    if (node.type != MarkdownTokenTypes.TEXT || node.endOffset <= fadeStart) return@markdownAnnotator false
    for (index in node.startOffset until node.endOffset) {
        val alpha = ((frontier - index) / tail).coerceIn(0f, 1f)
        if (alpha >= 1f) {
            append(content[index])
        } else {
            withStyle(SpanStyle(color = color.copy(alpha = alpha))) { append(content[index]) }
        }
    }
    true
}

private val TypingStyle.fadeTailChars: Int
    get() = if (mode == TypingMode.FADE) (charsPerSecond * FADE_SECONDS).toInt().coerceAtLeast(MIN_FADE_TAIL) else 0

private val PlainAnnotator: MarkdownAnnotator = markdownAnnotator()

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_LAG_SECONDS = 1.5f
private const val CATCH_UP_PER_SECOND = 3f
private const val FADE_SECONDS = 0.4f
private const val MIN_FADE_TAIL = 8
