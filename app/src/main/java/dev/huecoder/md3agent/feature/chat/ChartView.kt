package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.tools.ChartKind
import dev.huecoder.md3agent.tools.ChartSpec
import java.text.DecimalFormat
import kotlin.math.abs

@Composable
fun ChartCard(spec: ChartSpec, modifier: Modifier = Modifier) {
    val palette = chartPalette()
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.widthIn(max = CHART_MAX_WIDTH)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            spec.title?.let { Text(it, style = MaterialTheme.typography.titleSmallEmphasized) }
            when (spec.kind) {
                ChartKind.PIE -> PieChart(spec.series.single().values, palette, Modifier.fillMaxWidth().aspectRatio(PIE_ASPECT_RATIO))
                ChartKind.LINE, ChartKind.BAR -> AxisChart(spec, palette, Modifier.fillMaxWidth().height(AXIS_CHART_HEIGHT))
            }
            Legend(spec, palette)
        }
    }
}

@Composable
private fun Legend(spec: ChartSpec, palette: List<Color>) {
    val entries = when (spec.kind) {
        ChartKind.PIE -> {
            val values = spec.series.single().values
            val total = values.sum().takeIf { it > 0 } ?: 1.0
            spec.labels.mapIndexed { index, label -> label + " · " + formatValue(values[index], spec.unit) + " (" + PERCENT.format(values[index] / total) + ")" }
        }
        ChartKind.LINE, ChartKind.BAR -> spec.series.map { it.name }.takeIf { names -> names.size > 1 && names.any(String::isNotBlank) }.orEmpty()
    }
    if (entries.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(Modifier.size(LEGEND_DOT), shape = CircleShape, color = palette[index % palette.size]) {}
                Text(entry, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PieChart(values: List<Double>, palette: List<Color>, modifier: Modifier) {
    val total = values.sum()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        val diameter = minOf(size.width, size.height)
        val stroke = diameter * DONUT_STROKE_FRACTION
        val arcSize = Size(diameter - stroke, diameter - stroke)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
        val style = Stroke(width = stroke)
        if (total <= 0) {
            drawArc(track, 0f, FULL_CIRCLE, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            return@Canvas
        }
        var start = START_ANGLE
        values.forEachIndexed { index, value ->
            val sweep = (value / total * FULL_CIRCLE).toFloat()
            val gap = if (values.count { it > 0 } > 1) SLICE_GAP_DEGREES.coerceAtMost(sweep / 2) else 0f
            drawArc(palette[index % palette.size], start + gap / 2, sweep - gap, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            start += sweep
        }
    }
}

@Composable
internal fun chartPalette(): List<Color> = with(MaterialTheme.colorScheme) {
    listOf(primary, tertiary, secondary, error, primaryContainer.copy(alpha = 1f), tertiaryContainer)
}

internal fun formatValue(value: Double, unit: String?): String {
    val number = if (value == Math.rint(value) && abs(value) < WHOLE_NUMBER_LIMIT) value.toLong().toString() else DECIMAL.format(value)
    return if (unit.isNullOrBlank()) number else "$number $unit"
}

private val DECIMAL = DecimalFormat("#,##0.##")
private val PERCENT = DecimalFormat("0.#%")
private const val WHOLE_NUMBER_LIMIT = 1e15
private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f
private const val SLICE_GAP_DEGREES = 2f
private const val DONUT_STROKE_FRACTION = 0.22f
private const val PIE_ASPECT_RATIO = 1.6f
private val CHART_MAX_WIDTH = 480.dp
private val AXIS_CHART_HEIGHT = 200.dp
private val LEGEND_DOT = 10.dp
