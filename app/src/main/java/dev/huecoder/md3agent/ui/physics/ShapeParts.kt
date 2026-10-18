package dev.huecoder.md3agent.ui.physics

import androidx.compose.ui.geometry.Offset
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.RoundedPolygon
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.avatar.BotGeometry
import dev.huecoder.md3agent.ui.avatar.polygon
import org.dyn4j.geometry.Polygon
import org.dyn4j.geometry.Vector2
import org.dyn4j.geometry.decompose.Bayazit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

object ShapeParts {
    private val cache = ConcurrentHashMap<AgentShape, List<List<Offset>>>()

    fun of(shape: AgentShape): List<List<Offset>> = cache.getOrPut(shape) { decompose(outline(shape.polygon)) }

    fun outline(polygon: RoundedPolygon): List<Offset> {
        val flattened = ArrayList<Offset>()
        polygon.cubics.forEach { flatten(it, 0, flattened) }
        return counterClockwise(spaced(flattened))
    }

    fun decompose(outline: List<Offset>): List<List<Offset>> = mergeConvex(
        Bayazit().decompose(outline.map { Vector2(it.x.toDouble(), it.y.toDouble()) })
            .map { convex -> (convex as Polygon).vertices.map { Offset(it.x.toFloat(), it.y.toFloat()) } },
    )

    fun area(points: List<Offset>): Float = abs(signedArea(points))

    private fun flatten(cubic: Cubic, depth: Int, into: MutableList<Offset>) {
        if (depth >= MAX_SPLIT_DEPTH || cubic.isFlat()) {
            into += Offset(cubic.anchor0X - BotGeometry.CENTER, cubic.anchor0Y - BotGeometry.CENTER)
            return
        }
        val (head, tail) = cubic.split(HALF)
        flatten(head, depth + 1, into)
        flatten(tail, depth + 1, into)
    }

    private fun Cubic.isFlat(): Boolean {
        val chordX = anchor1X - anchor0X
        val chordY = anchor1Y - anchor0Y
        val chord = hypot(chordX, chordY)
        return maxOf(
            deviation(control0X - anchor0X, control0Y - anchor0Y, chordX, chordY, chord),
            deviation(control1X - anchor0X, control1Y - anchor0Y, chordX, chordY, chord),
        ) <= FLATNESS
    }

    private fun deviation(pointX: Float, pointY: Float, chordX: Float, chordY: Float, chord: Float): Float =
        if (chord < MIN_EDGE) hypot(pointX, pointY) else abs(pointX * chordY - pointY * chordX) / chord

    private fun mergeConvex(parts: List<List<Offset>>): List<List<Offset>> {
        val merged = parts.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            search@ for (first in merged.indices) {
                for (second in first + 1 until merged.size) {
                    val union = union(merged[first], merged[second]) ?: continue
                    merged[first] = union
                    merged.removeAt(second)
                    changed = true
                    break@search
                }
            }
        }
        return merged
    }

    private fun union(first: List<Offset>, second: List<Offset>): List<Offset>? {
        for (p in first.indices) {
            val from = first[p]
            val to = first[(p + 1) % first.size]
            val q = second.indices.firstOrNull { index -> same(second[index], to) && same(second[(index + 1) % second.size], from) } ?: continue
            val ring = (1..first.size).map { first[(p + it) % first.size] } + (2 until second.size).map { second[(q + it) % second.size] }
            return withoutFlatCorners(ring).takeIf { isConvex(it) }
        }
        return null
    }

    private fun withoutFlatCorners(ring: List<Offset>): List<Offset> = ring.filterIndexed { index, corner ->
        abs(turn(ring[(index + ring.size - 1) % ring.size], corner, ring[(index + 1) % ring.size])) > FLAT_CORNER
    }

    private fun isConvex(ring: List<Offset>): Boolean =
        ring.size >= MIN_CORNERS && ring.indices.all { turn(ring[it], ring[(it + 1) % ring.size], ring[(it + 2) % ring.size]) > 0f }

    private fun turn(a: Offset, b: Offset, c: Offset): Float = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)

    private fun same(a: Offset, b: Offset): Boolean = (a - b).getDistance() < MIN_EDGE

    private fun spaced(points: List<Offset>): List<Offset> {
        val kept = ArrayList<Offset>(points.size)
        points.forEach { point -> if (kept.isEmpty() || (point - kept.last()).getDistance() >= MIN_EDGE) kept += point }
        if (kept.size > 1 && (kept.last() - kept.first()).getDistance() < MIN_EDGE) kept.removeAt(kept.lastIndex)
        return kept
    }

    private fun counterClockwise(points: List<Offset>): List<Offset> = if (signedArea(points) < 0f) points.reversed() else points

    private fun signedArea(points: List<Offset>): Float {
        var twice = 0f
        points.forEachIndexed { index, from ->
            val to = points[(index + 1) % points.size]
            twice += from.x * to.y - to.x * from.y
        }
        return twice / 2f
    }

    private const val FLATNESS = 0.0009f
    private const val MIN_EDGE = 0.0001f
    private const val FLAT_CORNER = 1e-7f
    private const val MIN_CORNERS = 3
    private const val MAX_SPLIT_DEPTH = 12
    private const val HALF = 0.5f
}
