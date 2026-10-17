package dev.huecoder.md3agent.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetGeometryTest {
    private val windowHeight = 2000f
    private val halfHeight = 900f
    private val panel = SheetGeometry(windowHeight, halfHeight, compactHeight = null)
    private val compact = SheetGeometry(windowHeight, halfHeight, compactHeight = 300f)

    @Test
    fun `panel anchors sit where the sheet is full, half and hidden`() {
        val anchors = panel.anchors()
        assertEquals(0f, anchors.positionOf(SheetValue.Full), 0f)
        assertEquals(1100f, anchors.positionOf(SheetValue.Half), 0f)
        assertEquals(2000f, anchors.positionOf(SheetValue.Hidden), 0f)
        assertTrue(anchors.positionOf(SheetValue.Compact).isNaN())
    }

    @Test
    fun `compact adds an anchor between half and hidden`() {
        val anchors = compact.anchors()
        assertEquals(1700f, anchors.positionOf(SheetValue.Compact), 0f)
        assertTrue(anchors.positionOf(SheetValue.Half) < anchors.positionOf(SheetValue.Compact))
    }

    @Test
    fun `without a keyboard the sheet top is the dragged offset`() {
        listOf(0f, 400f, 1100f, 1500f, 2000f).forEach { offset ->
            assertEquals(offset, panel.top(offset, keyboard = 0f), 0.01f)
        }
    }

    @Test
    fun `a keyboard lifts the half position above it and shrinks the sheet to the available space`() {
        val keyboard = 800f
        val available = windowHeight - keyboard
        val top = panel.top(panel.halfOffset, keyboard)
        assertEquals(available - available * 0.7f, top, 0.01f)
        assertTrue(top < panel.halfOffset)
    }

    @Test
    fun `the full position stays at the top whatever the keyboard does`() {
        assertEquals(0f, panel.top(0f, keyboard = 800f), 0f)
    }

    @Test
    fun `the top moves smoothly with the keyboard while the sheet rests at half`() {
        var previous = panel.top(panel.halfOffset, keyboard = 0f)
        (1..80).forEach { step ->
            val top = panel.top(panel.halfOffset, keyboard = step * 10f)
            assertTrue(top <= previous)
            previous = top
        }
    }

    @Test
    fun `the top never decreases as the sheet is dragged down`() {
        val keyboard = 700f
        var previous = -1f
        (0..200).forEach { step ->
            val top = compact.top(step * 10f, keyboard)
            assertTrue(top >= previous)
            previous = top
        }
    }

    @Test
    fun `compact content rests right above the keyboard`() {
        val keyboard = 700f
        assertEquals(windowHeight - keyboard - 300f, compact.top(compact.compactOffset!!, keyboard), 0.01f)
    }

    @Test
    fun `chrome is hidden at compact and complete at half`() {
        assertEquals(0f, compact.chrome(compact.compactOffset!!), 0f)
        assertEquals(1f, compact.chrome(compact.halfOffset), 0f)
        assertEquals(1f, compact.chrome(0f), 0f)
        assertEquals(0f, compact.chrome(windowHeight), 0f)
        assertEquals(1f, panel.chrome(windowHeight), 0f)
    }

    @Test
    fun `openness grows from hidden to full`() {
        assertEquals(0f, panel.openness(windowHeight), 0f)
        assertEquals(1f, panel.openness(0f), 0f)
        assertEquals(0.45f, panel.openness(panel.halfOffset), 0.001f)
    }
}
