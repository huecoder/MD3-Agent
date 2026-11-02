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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.tools.ChartKind
import dev.huecoder.md3agent.tools.ChartSpec
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ChartCard(spec: ChartSpec, modifier: Modifier = Modifier) {
    val palette = chartPalette(spec.paletteSize())
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.widthIn(max = CHART_MAX_WIDTH)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            spec.title?.let { Text(it, style = MaterialTheme.typography.titleSmallEmphasized) }
            when (spec.kind) {
                ChartKind.PIE -> PieChart(spec.series.single().values, palette, Modifier.fillMaxWidth().aspectRatio(PIE_ASPECT_RATIO).padding(vertical = PIE_VERTICAL_PADDING))
                ChartKind.LINE, ChartKind.BAR -> AxisChart(spec, palette, Modifier.fillMaxWidth().height(AXIS_CHART_HEIGHT))
            }
            Legend(spec, palette)
        }
    }
}

private fun ChartSpec.paletteSize(): Int = when (kind) {
    ChartKind.PIE -> maxOf(labels.size, series.single().values.size)
    ChartKind.LINE, ChartKind.BAR -> series.size
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
internal fun PieChart(values: List<Double>, palette: List<Color>, modifier: Modifier) {
    val total = values.sum()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        val outer = minOf(size.width, size.height) / 2
        val inner = outer - outer * 2 * DONUT_STROKE_FRACTION
        val shown = values.count { it > 0 }
        when {
            total <= 0 -> drawRing(track, outer, inner)
            shown == 1 -> drawRing(palette[values.indexOfFirst { it > 0 } % palette.size], outer, inner)
            else -> {
                val middle = (outer + inner) / 2
                val minSweep = Math.toDegrees((outer - inner) * MIN_SLICE_WIDTH_FRACTION / middle.toDouble()).toFloat()
                var start = START_ANGLE
                pieSweeps(values, minSweep).forEachIndexed { index, sweep ->
                    if (sweep > 0f) drawSlice(palette[index % palette.size], start, sweep, outer, inner)
                    start += sweep
                }
            }
        }
    }
}

private fun DrawScope.drawRing(color: Color, outer: Float, inner: Float) {
    val radius = (outer + inner) / 2
    drawArc(
        color,
        startAngle = 0f,
        sweepAngle = FULL_CIRCLE,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = outer - inner),
    )
}

private fun DrawScope.drawSlice(color: Color, start: Float, sweep: Float, outer: Float, inner: Float) {
    val thickness = outer - inner
    val chord = inner * sin(Math.toRadians(minOf(sweep, STRAIGHT_ANGLE).toDouble() / 2)).toFloat()
    val halfGap = minOf(thickness * GAP_FRACTION / 2, chord * MAX_GAP_SHARE)
    val corner = minOf(thickness * CORNER_FRACTION, thickness / 2, chord - halfGap)
    val end = start + sweep
    val outerReach = outer - corner
    val innerReach = inner + corner
    val outerTilt = cornerTilt(outerReach, halfGap + corner)
    val innerTilt = cornerTilt(innerReach, halfGap + corner)
    val shape = Path().apply {
        arcTo(cornerRect(outerReach, start + outerTilt, corner), start - QUARTER_TURN, QUARTER_TURN + outerTilt, forceMoveTo = true)
        arcTo(Rect(center, outer), start + outerTilt, sweep - 2 * outerTilt, forceMoveTo = false)
        arcTo(cornerRect(outerReach, end - outerTilt, corner), end - outerTilt, QUARTER_TURN + outerTilt, forceMoveTo = false)
        arcTo(cornerRect(innerReach, end - innerTilt, corner), end + QUARTER_TURN, QUARTER_TURN - innerTilt, forceMoveTo = false)
        arcTo(Rect(center, inner), end - innerTilt, -(sweep - 2 * innerTilt), forceMoveTo = false)
        arcTo(cornerRect(innerReach, start + innerTilt, corner), start + innerTilt + STRAIGHT_ANGLE, QUARTER_TURN - innerTilt, forceMoveTo = false)
        close()
    }
    drawPath(shape, color)
}

private fun cornerTilt(reach: Float, offset: Float): Float =
    Math.toDegrees(atan2(offset, sqrt(reach * reach - offset * offset)).toDouble()).toFloat()

private fun DrawScope.cornerRect(reach: Float, angle: Float, radius: Float): Rect {
    val radians = Math.toRadians(angle.toDouble())
    return Rect(Offset(center.x + reach * cos(radians).toFloat(), center.y + reach * sin(radians).toFloat()), radius)
}

@Composable
internal fun chartPalette(count: Int): List<Color> {
    val seed = MaterialTheme.colorScheme.primary
    return remember(seed, count) { distinctColors(seed, count) }
}

internal fun distinctColors(seed: Color, count: Int): List<Color> {
    val base = seed.toHsl()
    val saturation = base.saturation.coerceAtLeast(MIN_SATURATION)
    return List(count.coerceAtLeast(1)) { index ->
        if (index == 0) {
            seed
        } else {
            val hue = (base.hue + index * GOLDEN_ANGLE) % FULL_CIRCLE
            val lightness = (base.lightness + LIGHTNESS_SHIFTS[index % LIGHTNESS_SHIFTS.size]).coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS)
            Color.hsl(hue, saturation, lightness)
        }
    }
}

private class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

private fun Color.toHsl(): Hsl {
    val high = maxOf(red, green, blue)
    val low = minOf(red, green, blue)
    val spread = high - low
    val lightness = (high + low) / 2
    if (spread == 0f) return Hsl(0f, 0f, lightness)
    val saturation = spread / (1f - abs(2f * lightness - 1f))
    val sector = when (high) {
        red -> ((green - blue) / spread).mod(HUE_SECTORS)
        green -> (blue - red) / spread + 2f
        else -> (red - green) / spread + 4f
    }
    return Hsl(sector * HUE_SECTOR_DEGREES, saturation, lightness)
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
private const val DONUT_STROKE_FRACTION = 0.22f
private const val CORNER_FRACTION = 0.2f
private const val GAP_FRACTION = 0.12f
private const val MIN_SLICE_WIDTH_FRACTION = 0.6f
private const val MAX_GAP_SHARE = 0.35f
private const val QUARTER_TURN = 90f
private const val STRAIGHT_ANGLE = 180f
private const val GOLDEN_ANGLE = 137.508f
private const val HUE_SECTORS = 6f
private const val HUE_SECTOR_DEGREES = 60f
private const val MIN_SATURATION = 0.5f
private const val MIN_LIGHTNESS = 0.35f
private const val MAX_LIGHTNESS = 0.8f
private val LIGHTNESS_SHIFTS = floatArrayOf(0f, 0.07f, -0.07f)
private val PIE_VERTICAL_PADDING = 4.dp
private const val PIE_ASPECT_RATIO = 1.6f
private val CHART_MAX_WIDTH = 480.dp
private val AXIS_CHART_HEIGHT = 200.dp
private val LEGEND_DOT = 10.dp
