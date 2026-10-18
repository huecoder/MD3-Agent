package dev.huecoder.md3agent.ui.physics

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.avatar.polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapePartsTest {
    @Test
    fun `every shape splits into a sane number of convex parts`() {
        AgentShape.entries.forEach { shape ->
            val parts = ShapeParts.of(shape)
            assertTrue("$shape gave ${parts.size} parts", parts.size in 1..MAX_PARTS)
        }
    }

    @Test
    fun `the parts add up to the area of the outline`() {
        AgentShape.entries.forEach { shape ->
            val outlineArea = ShapeParts.area(ShapeParts.outline(shape.polygon)).toDouble()
            val partsArea = ShapeParts.of(shape).sumOf { ShapeParts.area(it).toDouble() }
            assertEquals(shape.name, outlineArea, partsArea, outlineArea * AREA_TOLERANCE)
        }
    }

    @Test
    fun `a point is inside the parts exactly when it is inside the outline`() {
        AgentShape.entries.forEach { shape ->
            val outline = ShapeParts.outline(shape.polygon)
            val parts = ShapeParts.of(shape)
            gridPoints().filter { distanceToOutline(it, outline) > BOUNDARY_MARGIN }.forEach { point ->
                assertEquals("$shape at $point", insideOutline(point, outline), parts.any { insideConvex(point, it) })
            }
        }
    }

    @Test
    fun `a concave shape really has empty notches`() {
        val outline = ShapeParts.outline(AgentShape.CLOVER_4.polygon)
        val hull = convexHull(outline)
        val notch = gridPoints().filter { insideConvex(it, hull) && !insideOutline(it, outline) }.maxBy { distanceToOutline(it, outline) }
        assertTrue(ShapeParts.of(AgentShape.CLOVER_4).none { insideConvex(notch, it) })
        assertTrue(distanceToOutline(notch, outline) > NOTCH_DEPTH)
    }

    private fun gridPoints(): List<Offset> = (0..GRID_STEPS).flatMap { column ->
        (0..GRID_STEPS).map { row -> Offset(column.toFloat() / GRID_STEPS - HALF, row.toFloat() / GRID_STEPS - HALF) }
    }

    private companion object {
        const val MAX_PARTS = 80
        const val AREA_TOLERANCE = 0.01
        const val BOUNDARY_MARGIN = 0.008f
        const val NOTCH_DEPTH = 0.05f
        const val GRID_STEPS = 60
        const val HALF = 0.5f
    }
}

internal fun insideOutline(point: Offset, outline: List<Offset>): Boolean {
    var inside = false
    outline.forEachIndexed { index, from ->
        val to = outline[(index + 1) % outline.size]
        val crosses = (from.y > point.y) != (to.y > point.y)
        if (crosses && point.x < (to.x - from.x) * (point.y - from.y) / (to.y - from.y) + from.x) inside = !inside
    }
    return inside
}

internal fun insideConvex(point: Offset, part: List<Offset>): Boolean = part.indices.all { index ->
    val from = part[index]
    val to = part[(index + 1) % part.size]
    (to.x - from.x) * (point.y - from.y) - (to.y - from.y) * (point.x - from.x) >= 0f
}

internal fun distanceToOutline(point: Offset, outline: List<Offset>): Float = outline.indices.minOf { index ->
    val from = outline[index]
    val to = outline[(index + 1) % outline.size]
    val edge = to - from
    val along = (((point - from).x * edge.x + (point - from).y * edge.y) / edge.getDistanceSquared()).coerceIn(0f, 1f)
    (point - (from + edge * along)).getDistance()
}

internal fun convexHull(points: List<Offset>): List<Offset> {
    val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))
    fun turn(a: Offset, b: Offset, c: Offset) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    fun chain(ordered: List<Offset>): List<Offset> {
        val chain = mutableListOf<Offset>()
        ordered.forEach { point ->
            while (chain.size >= 2 && turn(chain[chain.size - 2], chain.last(), point) <= 0f) chain.removeAt(chain.lastIndex)
            chain += point
        }
        return chain.dropLast(1)
    }
    return chain(sorted) + chain(sorted.reversed())
}
