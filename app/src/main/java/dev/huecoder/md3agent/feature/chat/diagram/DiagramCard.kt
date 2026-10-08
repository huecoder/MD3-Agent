package dev.huecoder.md3agent.feature.chat.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.tools.DiagramNodeKind
import dev.huecoder.md3agent.tools.DiagramSpec
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DiagramCard(spec: DiagramSpec, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val nodeStyle = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center)
    val edgeStyle = MaterialTheme.typography.labelSmall
    val scene = remember(spec, density, nodeStyle, edgeStyle) {
        with(density) {
            val maxText = (NODE_MAX_WIDTH - NODE_PADDING_X * 2).roundToPx()
            val texts = spec.nodes.associate { node -> node.id to measurer.measure(node.label, nodeStyle, constraints = Constraints(maxWidth = maxText)) }
            val sizes = texts.mapValues { (_, text) ->
                maxOf(text.size.width + NODE_PADDING_X.toPx() * 2, NODE_MIN_WIDTH.toPx()) to maxOf(text.size.height + NODE_PADDING_Y.toPx() * 2, NODE_MIN_HEIGHT.toPx())
            }
            val metrics = DiagramMetrics(LAYER_GAP.toPx(), NODE_GAP.toPx(), LOOP_OFFSET.toPx(), MARGIN.toPx())
            val labels = spec.edges.map { edge -> edge.label?.let { measurer.measure(it, edgeStyle) } }
            DiagramScene(DiagramLayouter.layout(spec, sizes, metrics), texts, labels)
        }
    }
    val width = with(density) { scene.layout.width.toDp() }
    val height = with(density) { scene.layout.height.toDp() }

    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.title?.let { Text(it, style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.padding(horizontal = 16.dp)) }
            Canvas(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp).size(width, height)) {
                scene.layout.edges.forEach { drawArrow(it, colors.outline) }
                spec.nodes.forEach { node ->
                    drawNode(scene.layout.boxes.getValue(node.id), node.kind, scene.texts.getValue(node.id), colors)
                }
                scene.layout.edges.forEachIndexed { index, edge -> scene.edgeLabels[index]?.let { drawEdgeLabel(edge, it, colors) } }
            }
        }
    }
}

private class DiagramScene(val layout: DiagramLayout, val texts: Map<String, TextLayoutResult>, val edgeLabels: List<TextLayoutResult?>)

private fun DrawScope.drawArrow(edge: RoutedEdge, color: Color) {
    val path = Path().apply {
        moveTo(edge.start.x, edge.start.y)
        edge.segments.forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
    }
    drawPath(path, color, style = Stroke(width = EDGE_WIDTH.toPx(), cap = StrokeCap.Round))
    val angle = atan2(edge.endTangent.y, edge.endTangent.x)
    val length = ARROW_LENGTH.toPx()
    val spread = ARROW_SPREAD
    val head = Path().apply {
        moveTo(edge.end.x, edge.end.y)
        lineTo(edge.end.x - length * cos(angle - spread), edge.end.y - length * sin(angle - spread))
        lineTo(edge.end.x - length * cos(angle + spread), edge.end.y - length * sin(angle + spread))
        close()
    }
    drawPath(head, color)
}

private fun DrawScope.drawNode(box: NodeBox, kind: DiagramNodeKind, text: TextLayoutResult, colors: ColorScheme) {
    val (container, content) = when (kind) {
        DiagramNodeKind.STEP -> colors.surfaceContainerHighest to colors.onSurface
        DiagramNodeKind.DECISION -> colors.tertiaryContainer to colors.onTertiaryContainer
        DiagramNodeKind.START, DiagramNodeKind.END -> colors.primary to colors.onPrimary
        DiagramNodeKind.NOTE -> colors.surfaceContainer to colors.onSurfaceVariant
    }
    val corner = when (kind) {
        DiagramNodeKind.START, DiagramNodeKind.END -> box.height / 2f
        DiagramNodeKind.DECISION -> DECISION_CORNER.toPx()
        else -> STEP_CORNER.toPx()
    }
    val topLeft = Offset(box.x, box.y)
    val size = Size(box.width, box.height)
    drawRoundRect(container, topLeft, size, CornerRadius(corner))
    if (kind == DiagramNodeKind.NOTE) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx()))
        drawRoundRect(colors.outline, topLeft, size, CornerRadius(corner), style = Stroke(width = EDGE_WIDTH.toPx(), pathEffect = dash))
    }
    val textOffset = Offset(box.x + (box.width - text.size.width) / 2f, box.y + (box.height - text.size.height) / 2f)
    drawText(text, color = content, topLeft = textOffset)
}

private fun DrawScope.drawEdgeLabel(edge: RoutedEdge, text: TextLayoutResult, colors: ColorScheme) {
    val middle = edge.midpoint()
    val padX = LABEL_PADDING_X.toPx()
    val padY = LABEL_PADDING_Y.toPx()
    val size = Size(text.size.width + padX * 2, text.size.height + padY * 2)
    val topLeft = Offset(middle.x - size.width / 2f, middle.y - size.height / 2f)
    drawRoundRect(colors.secondaryContainer, topLeft, size, CornerRadius(size.height / 2f))
    drawText(text, color = colors.onSecondaryContainer, topLeft = topLeft + Offset(padX, padY))
}

private val NODE_MAX_WIDTH = 168.dp
private val NODE_MIN_WIDTH = 72.dp
private val NODE_MIN_HEIGHT = 40.dp
private val NODE_PADDING_X = 14.dp
private val NODE_PADDING_Y = 10.dp
private val LAYER_GAP = 44.dp
private val NODE_GAP = 16.dp
private val LOOP_OFFSET = 40.dp
private val MARGIN = 8.dp
private val EDGE_WIDTH = 2.dp
private val ARROW_LENGTH = 9.dp
private const val ARROW_SPREAD = 0.45f
private val STEP_CORNER = 14.dp
private val DECISION_CORNER = 6.dp
private val DASH = 4.dp
private val LABEL_PADDING_X = 6.dp
private val LABEL_PADDING_Y = 2.dp
