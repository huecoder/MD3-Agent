package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.tools.ChartKind
import dev.huecoder.md3agent.tools.ChartSpec
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
internal fun AxisChart(spec: ChartSpec, palette: List<Color>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val scale = remember(spec) { ValueScale.of(spec.series.flatMap { it.values }) }

    Canvas(modifier) {
        val tickLabels = scale.ticks.map { measurer.measure(formatValue(it, null), labelStyle) }
        val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + AXIS_GAP.toPx()
        val labelHeight = measurer.measure("0", labelStyle).size.height
        val bottom = size.height - labelHeight - AXIS_GAP.toPx()
        val plot = PlotArea(left, labelHeight / 2f, size.width, bottom)

        scale.ticks.forEachIndexed { index, tick ->
            val y = plot.y(scale.fraction(tick))
            drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = GRID_STROKE.toPx())
            val label = tickLabels[index]
            drawText(label, topLeft = Offset(plot.left - AXIS_GAP.toPx() - label.size.width, y - label.size.height / 2f))
        }
        drawCategoryLabels(spec, measurer, labelStyle, plot, size.height - labelHeight)
        when (spec.kind) {
            ChartKind.BAR -> drawBars(spec, scale, plot, palette)
            ChartKind.LINE -> drawLines(spec, scale, plot, palette)
            ChartKind.PIE -> Unit
        }
    }
}

private class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left

    fun y(fraction: Float) = bottom - (bottom - top) * fraction

    fun slot(index: Int, count: Int) = width / count * index

    fun pointX(index: Int, count: Int) = if (count == 1) left + width / 2 else left + width * index / (count - 1)
}

private class ValueScale(val low: Double, val high: Double, val ticks: List<Double>) {
    fun fraction(value: Double) = ((value - low) / (high - low)).toFloat()

    companion object {
        fun of(values: List<Double>): ValueScale {
            val min = minOf(0.0, values.minOrNull() ?: 0.0)
            val max = maxOf(0.0, values.maxOrNull() ?: 0.0).let { if (it == min) min + 1 else it }
            val rough = (max - min) / TARGET_TICKS
            val magnitude = 10.0.pow(floor(log10(rough)))
            val step = NICE_STEPS.first { it * magnitude >= rough } * magnitude
            val low = floor(min / step) * step
            val high = ceil(max / step) * step
            val count = ((high - low) / step).toInt()
            return ValueScale(low, high, (0..count).map { low + it * step })
        }
    }
}

private fun DrawScope.drawCategoryLabels(spec: ChartSpec, measurer: TextMeasurer, style: TextStyle, plot: PlotArea, top: Float) {
    val count = spec.labels.size
    val widest = spec.labels.maxOf { measurer.measure(it, style).size.width } + AXIS_GAP.toPx()
    val fits = (plot.width / widest).toInt().coerceAtLeast(1)
    val every = ceil(count.toDouble() / fits).toInt().coerceAtLeast(1)
    val maxWidth = (plot.width / count * every).toInt().coerceAtLeast(1)
    spec.labels.forEachIndexed { index, label ->
        if (index % every != 0) return@forEachIndexed
        val center = if (spec.kind == ChartKind.BAR) plot.left + plot.slot(index, count) + plot.width / count / 2 else plot.pointX(index, count)
        val layout = measurer.measure(label, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxWidth))
        val x = (center - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, top))
    }
}

private fun DrawScope.drawBars(spec: ChartSpec, scale: ValueScale, plot: PlotArea, palette: List<Color>) {
    val count = spec.labels.size
    val slot = plot.width / count
    val groupWidth = slot * BAR_GROUP_FRACTION
    val barWidth = groupWidth / spec.series.size
    val zero = plot.y(scale.fraction(0.0))
    val radius = CornerRadius(BAR_CORNER.toPx().coerceAtMost(barWidth / 2))
    spec.series.forEachIndexed { seriesIndex, series ->
        series.values.forEachIndexed { index, value ->
            val x = plot.left + plot.slot(index, count) + (slot - groupWidth) / 2 + barWidth * seriesIndex
            val y = plot.y(scale.fraction(value))
            drawRoundRect(
                palette[seriesIndex % palette.size],
                topLeft = Offset(x + BAR_GAP.toPx() / 2, minOf(y, zero)),
                size = Size((barWidth - BAR_GAP.toPx()).coerceAtLeast(1f), abs(zero - y)),
                cornerRadius = radius,
            )
        }
    }
}

private fun DrawScope.drawLines(spec: ChartSpec, scale: ValueScale, plot: PlotArea, palette: List<Color>) {
    val count = spec.labels.size
    spec.series.forEachIndexed { seriesIndex, series ->
        val color = palette[seriesIndex % palette.size]
        val points = series.values.mapIndexed { index, value -> Offset(plot.pointX(index, count), plot.y(scale.fraction(value))) }
        val path = Path().apply {
            points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        }
        drawPath(path, color, style = Stroke(LINE_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (count <= MAX_DOTTED_POINTS) points.forEach { drawCircle(color, POINT_RADIUS.toPx(), it) }
    }
}

private const val TARGET_TICKS = 4
private const val BAR_GROUP_FRACTION = 0.72f
private const val MAX_DOTTED_POINTS = 24
private val NICE_STEPS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
private val AXIS_GAP = 6.dp
private val GRID_STROKE = 1.dp
private val LINE_STROKE = 2.5.dp
private val POINT_RADIUS = 3.dp
private val BAR_GAP = 2.dp
private val BAR_CORNER = 4.dp
