package dev.huecoder.md3agent.feature.chat.diagram

import dev.huecoder.md3agent.tools.DiagramDirection
import dev.huecoder.md3agent.tools.DiagramSpec
import kotlin.math.abs
import kotlin.math.max

data class Point(val x: Float, val y: Float) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun times(scale: Float) = Point(x * scale, y * scale)
}

data class NodeBox(val x: Float, val y: Float, val width: Float, val height: Float) {
    val center get() = Point(x + width / 2f, y + height / 2f)
}

data class CubicSegment(val start: Point, val control1: Point, val control2: Point, val end: Point) {
    fun pointAt(t: Float): Point {
        val u = 1f - t
        return start * (u * u * u) + control1 * (3 * u * u * t) + control2 * (3 * u * t * t) + end * (t * t * t)
    }
}

data class RoutedEdge(val segments: List<CubicSegment>, val label: String?) {
    val start: Point get() = segments.first().start
    val end: Point get() = segments.last().end

    fun midpoint(): Point = segments[segments.size / 2].pointAt(if (segments.size % 2 == 0) 0f else MIDDLE)

    val endTangent: Point
        get() {
            val last = segments.last()
            return (last.end - last.control2).takeIf { abs(it.x) + abs(it.y) > EPSILON } ?: (last.end - last.start)
        }

    private companion object {
        const val EPSILON = 0.01f
        const val MIDDLE = 0.5f
    }
}

data class DiagramLayout(val width: Float, val height: Float, val boxes: Map<String, NodeBox>, val edges: List<RoutedEdge>)

data class DiagramMetrics(val layerGap: Float, val nodeGap: Float, val loopOffset: Float, val margin: Float)

object DiagramLayouter {
    fun layout(spec: DiagramSpec, sizes: Map<String, Pair<Float, Float>>, metrics: DiagramMetrics): DiagramLayout {
        val down = spec.direction == DiagramDirection.DOWN
        val graph = LayeredGraph.build(spec)
        val along = { id: String -> sizes[id]?.let { if (down) it.second else it.first } ?: 0f }
        val across = { id: String -> sizes[id]?.let { if (down) it.first else it.second } ?: DUMMY_SPAN }
        val crossCenter = CrossPlacement.place(graph, across, metrics.nodeGap)
        val layerOf = graph.layers.withIndex().flatMap { (index, layer) -> layer.map { it to index } }.toMap()
        val bands = mutableListOf<Pair<Float, Float>>()
        var cursor = 0f
        graph.layers.forEach { layer ->
            val thickness = layer.maxOf(along)
            bands += cursor to cursor + thickness
            cursor += thickness + metrics.layerGap
        }
        val flow = if (down) Point(0f, 1f) else Point(1f, 0f)
        val cross = if (down) Point(1f, 0f) else Point(0f, 1f)
        fun at(alongPos: Float, crossPos: Float) = flow * alongPos + cross * crossPos

        val boxes = spec.nodes.associate { node ->
            val (w, h) = sizes.getValue(node.id)
            val band = bands[layerOf.getValue(node.id)]
            val c = at((band.first + band.second) / 2f, crossCenter.getValue(node.id))
            node.id to NodeBox(c.x - w / 2f, c.y - h / 2f, w, h)
        }
        fun halfAlong(box: NodeBox) = (if (down) box.height else box.width) / 2f
        fun halfCross(box: NodeBox) = (if (down) box.width else box.height) / 2f

        val routed = spec.edges.mapIndexed { index, edge ->
            val from = boxes.getValue(edge.from)
            val to = boxes.getValue(edge.to)
            val chain = graph.chains[index]
            val segments = if (chain != null) {
                val through = chain.flatMap { id ->
                    val band = bands[layerOf.getValue(id)]
                    listOf(at(band.first, crossCenter.getValue(id)), at(band.second, crossCenter.getValue(id)))
                }
                val points = listOf(from.center + flow * halfAlong(from)) + through + listOf(to.center - flow * halfAlong(to))
                points.zipWithNext { a, b -> smooth(a, b, flow, metrics.layerGap) }
            } else {
                listOf(loop(from, to, layerOf.getValue(edge.from)..layerOf.getValue(edge.to), graph, layerOf, crossCenter, across, metrics.loopOffset, cross, ::halfCross))
            }
            RoutedEdge(segments, edge.label)
        }
        return fitted(boxes, routed, metrics.margin)
    }

    private fun loop(
        from: NodeBox,
        to: NodeBox,
        span: IntRange,
        graph: LayeredGraph,
        layerOf: Map<String, Int>,
        crossCenter: Map<String, Float>,
        across: (String) -> Float,
        offset: Float,
        cross: Point,
        halfCross: (NodeBox) -> Float,
    ): CubicSegment {
        val crossOf = { p: Point -> p.x * cross.x + p.y * cross.y }
        val reached = minOf(span.first, span.last)..maxOf(span.first, span.last)
        val passing = graph.layers.flatten().filter { id -> layerOf.getValue(id) in reached }
        val lowest = passing.minOf { crossCenter.getValue(it) - across(it) / 2f }
        val highest = passing.maxOf { crossCenter.getValue(it) + across(it) / 2f }
        val near = maxOf(crossOf(from.center) + halfCross(from), crossOf(to.center) + halfCross(to))
        val far = minOf(crossOf(from.center) - halfCross(from), crossOf(to.center) - halfCross(to))
        val onHighSide = highest - near <= far - lowest
        val direction = if (onHighSide) 1f else -1f
        val start = from.center + cross * (direction * halfCross(from))
        val end = to.center + cross * (direction * halfCross(to))
        val outer = (if (onHighSide) highest + offset else lowest - offset)
        val push = { p: Point -> p + cross * (outer - crossOf(p)) }
        return CubicSegment(start, push(start), push(end), end)
    }

    private fun fitted(boxes: Map<String, NodeBox>, edges: List<RoutedEdge>, margin: Float): DiagramLayout {
        val points = boxes.values.flatMap { listOf(Point(it.x, it.y), Point(it.x + it.width, it.y + it.height)) } +
            edges.flatMap { edge -> edge.segments.flatMap { listOf(it.start, it.control1, it.control2, it.end) } }
        val shift = Point(margin - points.minOf { it.x }, margin - points.minOf { it.y })
        val moved = boxes.mapValues { (_, box) -> box.copy(x = box.x + shift.x, y = box.y + shift.y) }
        val movedEdges = edges.map { edge ->
            edge.copy(segments = edge.segments.map { CubicSegment(it.start + shift, it.control1 + shift, it.control2 + shift, it.end + shift) })
        }
        return DiagramLayout(points.maxOf { it.x } + shift.x + margin, points.maxOf { it.y } + shift.y + margin, moved, movedEdges)
    }

    private fun smooth(from: Point, to: Point, flow: Point, minReach: Float): CubicSegment {
        val distance = (to - from).let { it.x * flow.x + it.y * flow.y }
        val bend = flow * (max(distance, minReach) / 2f)
        return CubicSegment(from, from + bend, to - bend, to)
    }

    private const val DUMMY_SPAN = 12f
}
