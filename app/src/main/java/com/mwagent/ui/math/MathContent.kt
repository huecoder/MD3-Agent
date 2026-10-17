package com.mwagent.ui.math

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import com.mikepenz.markdown.model.DefaultMarkdownInlineContent
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownInlineContent
import com.mikepenz.markdown.model.markdownAnnotator
import org.intellij.markdown.MarkdownTokenTypes
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.min

@Stable
class MathContent internal constructor(
    val markdown: String,
    val inlineContent: MarkdownInlineContent,
    val measure: Modifier,
    private val formulas: List<MathFormula>,
) {
    fun annotator(base: MarkdownAnnotator): MarkdownAnnotator = markdownAnnotator(base.config) { content, node ->
        (node.type == MarkdownTokenTypes.TEXT && appendSlots(content.substring(node.startOffset, node.endOffset))) ||
            base.annotate?.invoke(this, content, node) == true
    }

    private fun AnnotatedString.Builder.appendSlots(text: String): Boolean {
        val slots = MathExtractor.slotPattern.findAll(text)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull()?.takeIf { it in formulas.indices }?.let { match to it } }
            .toList()
        if (slots.isEmpty()) return false
        var copied = 0
        slots.forEach { (match, index) ->
            append(text.substring(copied, match.range.first))
            appendInlineContent(inlineId(index), formulas[index].latex)
            copied = match.range.last + 1
        }
        append(text.substring(copied))
        return true
    }
}

@Composable
fun rememberMathContent(text: String): MathContent {
    val context = LocalContext.current
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize
    val sizePx = with(density) { fontSize.toPx() }
    var availableWidthPx by remember { mutableIntStateOf(0) }
    val measure = remember { Modifier.onSizeChanged { availableWidthPx = it.width } }
    return remember(text, color, sizePx, availableWidthPx, density) {
        val extracted = MathExtractor.extract(text) { FormulaDrawables.of(context, it, sizePx, color) != null }
        val inline = extracted.formulas.mapIndexed { index, formula ->
            inlineId(index) to formulaContent(context, formula, sizePx, color, availableWidthPx, density)
        }.toMap()
        MathContent(extracted.markdown, DefaultMarkdownInlineContent(inline), measure, extracted.formulas)
    }
}

private fun inlineId(index: Int) = "math-$index"

private fun formulaContent(context: Context, formula: MathFormula, sizePx: Float, color: Int, availableWidthPx: Int, density: Density): InlineTextContent {
    val drawable = checkNotNull(FormulaDrawables.of(context, formula, sizePx, color))
    val natural = drawable.intrinsicWidth.toFloat()
    val fitted = if (availableWidthPx > 0) min(1f, availableWidthPx / natural) else 1f
    val widthPx = if (formula.isDisplay && availableWidthPx > 0) availableWidthPx.toFloat() else natural * fitted
    val heightPx = drawable.intrinsicHeight * fitted
    val placeholder = with(density) {
        Placeholder(widthPx.toDp().toSp(), heightPx.toDp().toSp(), PlaceholderVerticalAlign.TextCenter)
    }
    return InlineTextContent(placeholder) { FormulaCanvas(drawable) }
}

@Composable
private fun FormulaCanvas(drawable: JLatexMathDrawable) {
    Canvas(Modifier.fillMaxSize()) {
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}
