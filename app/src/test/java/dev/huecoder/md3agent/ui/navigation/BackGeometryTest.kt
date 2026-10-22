package dev.huecoder.md3agent.ui.navigation

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class BackGeometryTest {
    private val linear = Easing { it }
    private val easings = BackEasings(gesture = linear, commit = linear)

    private fun geometry(pressedToRight: Boolean = true, startRadius: Float = 0f) =
        BackGeometry(width = 1000f, height = 2000f, startTouchY = 1000f, pressedToRight, startRadius, pxPerDp = 2f, easings)

    @Test
    fun `no progress keeps the card on the full screen`() {
        assertEquals(Rect(0f, 0f, 1000f, 2000f), geometry().gesture(0f, 1000f).closing)
    }

    @Test
    fun `swipe from the left presses the card to the right edge`() {
        val closing = geometry(pressedToRight = true).gesture(1f, 1000f).closing
        assertEquals(850f, closing.width, EPSILON)
        assertEquals(984f, closing.right, EPSILON)
    }

    @Test
    fun `swipe from the right keeps the card centred`() {
        val closing = geometry(pressedToRight = false).gesture(1f, 1000f).closing
        assertEquals(75f, closing.left, EPSILON)
        assertEquals(925f, closing.right, EPSILON)
    }

    @Test
    fun `previous screen waits outside the left edge`() {
        assertEquals(-167f, geometry().gesture(0f, 1000f).entering.left, EPSILON)
    }

    @Test
    fun `card follows the finger only inside the margin`() {
        val closing = geometry().gesture(1f, 3000f).closing
        assertEquals(284f, closing.top, EPSILON)
    }

    @Test
    fun `corner radius grows from the display radius`() {
        val frame = geometry(startRadius = 30f).gesture(1f, 1000f)
        assertEquals(80f, frame.cornerRadius, EPSILON)
    }

    @Test
    fun `commit ends on the previous screen with the card gone`() {
        val subject = geometry(startRadius = 30f)
        val last = subject.commit(subject.gesture(1f, 1000f), 1f)
        assertEquals(Rect(0f, 0f, 1000f, 2000f), last.entering)
        assertEquals(30f, last.cornerRadius, EPSILON)
        assertEquals(0f, last.closingAlpha, EPSILON)
        assertEquals(0f, last.scrim, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
