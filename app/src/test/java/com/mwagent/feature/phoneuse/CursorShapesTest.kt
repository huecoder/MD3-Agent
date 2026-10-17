package com.mwagent.feature.phoneuse

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorShapesTest {
    private fun nodes(data: String) = PathParser().parsePathString(data).toNodes()

    private fun points(nodes: List<PathNode>): List<Pair<Float, Float>> {
        var x = 0f
        var y = 0f
        val found = mutableListOf<Pair<Float, Float>>()
        nodes.forEach { node ->
            when (node) {
                is PathNode.MoveTo -> { x = node.x; y = node.y; found += x to y }
                is PathNode.LineTo -> { x = node.x; y = node.y; found += x to y }
                is PathNode.HorizontalTo -> { x = node.x; found += x to y }
                is PathNode.VerticalTo -> { y = node.y; found += x to y }
                is PathNode.CurveTo -> { found += node.x1 to node.y1; found += node.x2 to node.y2; x = node.x3; y = node.y3; found += x to y }
                PathNode.Close -> Unit
                else -> error("unexpected $node")
            }
        }
        return found
    }

    @Test
    fun `the modern pointer path parses into one closed outline`() {
        val parsed = nodes(BIBATA_MODERN_PATH)

        assertTrue(parsed.first() is PathNode.MoveTo)
        assertEquals(PathNode.Close, parsed.last())
        assertEquals(1, parsed.count { it is PathNode.MoveTo })
    }

    @Test
    fun `the original pointer path parses into one closed outline`() {
        val parsed = nodes(BIBATA_ORIGINAL_PATH)

        assertEquals(PathNode.Close, parsed.last())
        assertEquals(1, parsed.count { it is PathNode.MoveTo })
    }

    @Test
    fun `the modern outline fits the 256 canvas and the hotspot sits at its top left`() {
        val all = points(nodes(BIBATA_MODERN_PATH))
        val left = all.minOf { it.first }
        val top = all.minOf { it.second }

        assertTrue(all.all { it.first in 0f..BIBATA_CANVAS_UNITS && it.second in 0f..BIBATA_CANVAS_UNITS })
        assertTrue("hotspot x ${BIBATA_HOTSPOT.x} should be right of the outline's left edge $left", BIBATA_HOTSPOT.x > left - BIBATA_STROKE_UNITS)
        assertTrue("hotspot y ${BIBATA_HOTSPOT.y} should be above the outline's top edge $top", BIBATA_HOTSPOT.y < top)
    }

    @Test
    fun `the original outline fits the 256 canvas and the hotspot sits at its top left`() {
        val all = points(nodes(BIBATA_ORIGINAL_PATH))
        val left = all.minOf { it.first }
        val top = all.minOf { it.second }

        assertTrue(all.all { it.first in 0f..BIBATA_CANVAS_UNITS && it.second in 0f..BIBATA_CANVAS_UNITS })
        assertTrue(BIBATA_HOTSPOT.x > left - BIBATA_STROKE_UNITS)
        assertTrue(BIBATA_HOTSPOT.y < top)
    }
}
