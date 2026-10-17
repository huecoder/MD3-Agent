package com.mwagent.feature.chat

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
import com.mwagent.data.settings.TypingMode
import com.mwagent.data.settings.TypingStyle
import com.mwagent.ui.math.rememberMathContent
import com.mwagent.ui.pictures.MarkdownBlocks
import kotlinx.coroutines.flow.first
import org.intellij.markdown.MarkdownTokenTypes
import kotlin.math.ceil

@Composable
fun ReplyText(text: String, isStreaming: Boolean, style: TypingStyle, modifier: Modifier = Modifier, isStopped: Boolean = false) {
    val startedLive = remember { isStreaming }
    if (!style.isAnimated || !startedLive) {
        MarkdownContent(text, modifier)
        return
    }
    val tail = style.fadeTailChars
    val cuts = remember { GraphemeCuts() }
    val math = rememberMathContent(text)
    val revealed = math.markdown
    val frontier = rememberRevealFrontier(revealed, style.charsPerSecond, tail, isRunning = !isStopped)
    val shown = if (isStopped) revealed else revealed.substring(0, cuts.floor(revealed, ceil(frontier).toInt()))
    val blocks = remember(shown) { MarkdownBlocks.split(shown) }
    val color = MaterialTheme.colorScheme.onSurface
    MarkdownBlocksContent(blocks, math, modifier) { block ->
        val parsed = remember(block.markdown) { parseMarkdown(block.markdown) }
        val annotator = if (tail == 0 || isStopped) PlainAnnotator else fadeAnnotator(frontier - block.start, tail, color, cuts)
        MarkdownContent(parsed, annotator, math)
    }
}

@Composable
private fun rememberRevealFrontier(text: String, charsPerSecond: Int, tail: Int, isRunning: Boolean): Float {
    val frontier = remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(text)
    LaunchedEffect(charsPerSecond, tail, isRunning) {
        if (!isRunning) return@LaunchedEffect
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

private fun fadeAnnotator(frontier: Float, tail: Int, color: Color, cuts: GraphemeCuts): MarkdownAnnotator = markdownAnnotator { content, node ->
    val fadeStart = frontier - tail
    if (node.type != MarkdownTokenTypes.TEXT || node.endOffset <= fadeStart) return@markdownAnnotator false
    cuts.forEach(content, node.startOffset, node.endOffset) { from, to ->
        val alpha = ((frontier - from) / tail).coerceIn(0f, 1f)
        if (alpha >= 1f) {
            append(content, from, to)
        } else {
            withStyle(SpanStyle(color = color.copy(alpha = alpha))) { append(content, from, to) }
        }
    }
    true
}

private val TypingStyle.fadeTailChars: Int
    get() = if (mode == TypingMode.FADE) (charsPerSecond * FADE_SECONDS).toInt().coerceAtLeast(MIN_FADE_TAIL) else 0

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_LAG_SECONDS = 1.5f
private const val CATCH_UP_PER_SECOND = 3f
private const val FADE_SECONDS = 0.4f
private const val MIN_FADE_TAIL = 8
