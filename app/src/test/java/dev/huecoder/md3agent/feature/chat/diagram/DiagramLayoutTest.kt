package dev.huecoder.md3agent.feature.chat.diagram

import dev.huecoder.md3agent.tools.DiagramDirection
import dev.huecoder.md3agent.tools.DiagramEdge
import dev.huecoder.md3agent.tools.DiagramNode
import dev.huecoder.md3agent.tools.DiagramNodeKind
import dev.huecoder.md3agent.tools.DiagramSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DiagramLayoutTest {
    private val metrics = DiagramMetrics(layerGap = 40f, nodeGap = 16f, loopOffset = 40f, margin = 8f)

    private fun spec(direction: DiagramDirection = DiagramDirection.DOWN) = DiagramSpec(
        title = null,
        direction = direction,
        nodes = listOf("a", "b", "c", "d").map { DiagramNode(it, it, DiagramNodeKind.STEP) },
        edges = listOf(DiagramEdge("a", "b", null), DiagramEdge("a", "c", "yes"), DiagramEdge("b", "d", null), DiagramEdge("c", "d", null), DiagramEdge("d", "a", "retry")),
    )

    private val sizes = mapOf("a" to (100f to 40f), "b" to (80f to 40f), "c" to (120f to 60f), "d" to (100f to 40f))

    @Test
    fun `layers flow downward and siblings share a row`() {
        val layout = DiagramLayouter.layout(spec(), sizes, metrics)
        val box = layout.boxes
        assertTrue(box.getValue("a").y < box.getValue("b").y)
        assertEquals(box.getValue("b").center.y, box.getValue("c").center.y, 0.01f)
        assertTrue(box.getValue("d").y > box.getValue("c").y + box.getValue("c").height)
    }

    @Test
    fun `cycle edge loops around the side`() {
        val loop = DiagramLayouter.layout(spec(), sizes, metrics).edges.last()
        assertTrue(abs(loop.segments.single().control1.x - loop.start.x) > 0f)
        assertEquals("retry", loop.label)
    }

    @Test
    fun `everything fits inside the canvas`() {
        DiagramDirection.entries.forEach { direction ->
            val layout = DiagramLayouter.layout(spec(direction), sizes, metrics)
            layout.boxes.values.forEach { assertTrue(it.x >= 0f && it.y >= 0f && it.x + it.width <= layout.width && it.y + it.height <= layout.height) }
        }
    }
}
