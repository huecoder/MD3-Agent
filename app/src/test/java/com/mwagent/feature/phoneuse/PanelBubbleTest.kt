package com.mwagent.feature.phoneuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelBubbleTest {
    private val width = 1080
    private val height = 2400
    private val size = 154
    private val margin = 24

    @Test
    fun `the bubble rests against the end edge or the start edge`() {
        assertEquals(BubbleSpot(width - size - margin, 1488), PanelBubble.spot(width, height, size, margin, atEnd = true, heightShare = 0.62f))
        assertEquals(BubbleSpot(margin, 1488), PanelBubble.spot(width, height, size, margin, atEnd = false, heightShare = 0.62f))
    }

    @Test
    fun `the bubble never rests outside the screen`() {
        assertEquals(margin, PanelBubble.spot(width, height, size, margin, atEnd = true, heightShare = -1f).y)
        assertEquals(height - size - margin, PanelBubble.spot(width, height, size, margin, atEnd = true, heightShare = 2f).y)
    }

    @Test
    fun `a bubble dragged past the screen is pulled back`() {
        assertEquals(BubbleSpot(0, 0), PanelBubble.clamped(BubbleSpot(-50, -9), width, height, size))
        assertEquals(BubbleSpot(width - size, height - size), PanelBubble.clamped(BubbleSpot(5000, 9000), width, height, size))
    }

    @Test
    fun `a dropped bubble goes to the nearer edge`() {
        assertTrue(PanelBubble.staysAtEnd(left = 600, size = size, screenWidth = width))
        assertFalse(PanelBubble.staysAtEnd(left = 200, size = size, screenWidth = width))
    }

    @Test
    fun `the height is remembered as a share of the screen`() {
        assertEquals(0.5f, PanelBubble.heightShare(1200, height))
        assertEquals(0f, PanelBubble.heightShare(-5, height))
    }

    @Test
    fun `the same share lands halfway down the screen after rotation too`() {
        assertEquals(1200, PanelBubble.spot(1080, 2400, size, margin, atEnd = true, heightShare = 0.5f).y)
        assertEquals(540, PanelBubble.spot(2400, 1080, size, margin, atEnd = true, heightShare = 0.5f).y)
    }
}
