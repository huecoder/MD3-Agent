package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownTableBasicText
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

internal class TableGrid(val hasHeader: Boolean, private val lines: List<List<ASTNode>>) {
    val lineCount: Int = lines.size
    val columns: Int = lines.maxOfOrNull { it.size } ?: 0

    fun cell(line: Int, column: Int): ASTNode? = lines[line].getOrNull(column)

    companion object {
        fun of(table: ASTNode): TableGrid {
            val header = table.children.firstOrNull { it.type == GFMElementTypes.HEADER }.cells()
            val rows = table.children.filter { it.type == GFMElementTypes.ROW }.map { it.cells() }
            return TableGrid(header.isNotEmpty(), listOfNotNull(header.takeIf { it.isNotEmpty() }) + rows)
        }

        private fun ASTNode?.cells(): List<ASTNode> = this?.children?.filter { it.type == GFMTokenTypes.CELL }.orEmpty()
    }
}

@Composable
fun ChatTable(model: MarkdownComponentModel, modifier: Modifier = Modifier) {
    val grid = remember(model.node) { TableGrid.of(model.node) }
    if (grid.columns == 0 || grid.lineCount == 0) return
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val style = model.typography.table
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = constraints.maxWidth
        val policy = remember(grid, density, available) {
            GridMeasurePolicy(grid.lineCount, grid.columns, available, with(density) { MIN_COLUMN.roundToPx() }, with(density) { MAX_COLUMN.roundToPx() })
        }
        val scroll = rememberScrollState()
        val fade = colors.surfaceContainerLow
        val thumb = colors.onSurfaceVariant
        Box(
            Modifier
                .clip(TABLE_SHAPE)
                .background(colors.surfaceContainerLow)
                .drawWithContent {
                    drawContent()
                    if (scroll.maxValue > 0) drawScrollCues(scroll.value, scroll.maxValue, fade, thumb)
                },
        ) {
            Box(Modifier.horizontalScroll(scroll)) {
                Layout(
                    content = {
                        repeat(grid.lineCount) { line ->
                            repeat(grid.columns) { column ->
                                val isHeader = grid.hasHeader && line == 0
                                TableCell(
                                    content = model.content,
                                    cell = grid.cell(line, column),
                                    style = if (isHeader) style.copy(fontWeight = FontWeight.SemiBold) else style,
                                    background = if (isHeader) colors.surfaceContainerHigh else Color.Unspecified,
                                    rule = if (line < grid.lineCount - 1) colors.outlineVariant.copy(alpha = RULE_ALPHA) else Color.Unspecified,
                                    line = line,
                                    column = column,
                                    isHeader = isHeader,
                                )
                            }
                        }
                    },
                    modifier = Modifier.semantics { collectionInfo = CollectionInfo(rowCount = grid.lineCount, columnCount = grid.columns) },
                    measurePolicy = policy,
                )
            }
        }
    }
}

private fun ContentDrawScope.drawScrollCues(value: Int, maxValue: Int, fade: Color, thumb: Color) {
    val fadeWidth = SCROLL_FADE_WIDTH.toPx()
    if (value > 0) {
        drawRect(Brush.horizontalGradient(listOf(fade, Color.Transparent), endX = fadeWidth), size = Size(fadeWidth, size.height))
    }
    if (value < maxValue) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, fade), startX = size.width - fadeWidth, endX = size.width),
            topLeft = Offset(size.width - fadeWidth, 0f),
            size = Size(fadeWidth, size.height),
        )
    }
    val track = size.width - SCROLL_THUMB_INSET.toPx() * 2
    val thumbWidth = (track * size.width / (size.width + maxValue)).coerceAtLeast(SCROLL_THUMB_MIN.toPx())
    val left = SCROLL_THUMB_INSET.toPx() + (track - thumbWidth) * value / maxValue
    val height = SCROLL_THUMB_HEIGHT.toPx()
    drawRoundRect(
        thumb.copy(alpha = SCROLL_THUMB_ALPHA),
        topLeft = Offset(left, size.height - height - SCROLL_THUMB_INSET.toPx()),
        size = Size(thumbWidth, height),
        cornerRadius = CornerRadius(height / 2, height / 2),
    )
}

@Composable
private fun TableCell(content: String, cell: ASTNode?, style: TextStyle, background: Color, rule: Color, line: Int, column: Int, isHeader: Boolean) {
    Box(
        Modifier
            .then(if (background.isSpecified) Modifier.background(background) else Modifier)
            .drawBehind {
                if (rule.isSpecified) drawRect(rule, Offset(0f, size.height - RULE_THICKNESS.toPx()), Size(size.width, RULE_THICKNESS.toPx()))
            }
            .semantics {
                if (isHeader) heading()
                collectionItemInfo = CollectionItemInfo(rowIndex = line, rowSpan = 1, columnIndex = column, columnSpan = 1)
            }
            .padding(horizontal = CELL_HORIZONTAL_PADDING, vertical = CELL_VERTICAL_PADDING),
    ) {
        if (cell != null) MarkdownTableBasicText(content, cell, style, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
    }
}

private class GridMeasurePolicy(val lines: Int, val columns: Int, val available: Int, val minColumn: Int, val maxColumn: Int) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val widths = columnWidths(measurables)
        val heights = List(lines) { line -> widths.indices.maxOf { column -> measurables[line * columns + column].minIntrinsicHeight(widths[column]) } }
        val placeables = measurables.mapIndexed { index, measurable -> measurable.measure(Constraints.fixed(widths[index % columns], heights[index / columns])) }
        val xs = widths.runningFold(0, Int::plus)
        val ys = heights.runningFold(0, Int::plus)
        return layout(xs.last(), ys.last()) {
            placeables.forEachIndexed { index, placeable -> placeable.place(xs[index % columns], ys[index / columns]) }
        }
    }

    private fun columnWidths(measurables: List<Measurable>): List<Int> {
        val minimums = List(columns) { column -> columnOf(measurables, column).maxOf { it.minIntrinsicWidth(Constraints.Infinity) }.coerceAtLeast(minColumn) }
        val preferred = List(columns) { column ->
            columnOf(measurables, column).maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }.coerceIn(minimums[column], maxOf(minimums[column], maxColumn))
        }
        return TableColumns.widths(minimums, preferred, available)
    }

    private fun columnOf(measurables: List<Measurable>, column: Int): List<Measurable> = List(lines) { line -> measurables[line * columns + column] }
}

private val TABLE_SHAPE = RoundedCornerShape(12.dp)
private val MIN_COLUMN = 72.dp
private val MAX_COLUMN = 200.dp
private val CELL_HORIZONTAL_PADDING = 10.dp
private val CELL_VERTICAL_PADDING = 8.dp
private val RULE_THICKNESS = 1.dp
private const val RULE_ALPHA = 0.6f
private val SCROLL_FADE_WIDTH = 28.dp
private val SCROLL_THUMB_HEIGHT = 3.dp
private val SCROLL_THUMB_INSET = 6.dp
private val SCROLL_THUMB_MIN = 24.dp
private const val SCROLL_THUMB_ALPHA = 0.55f
