package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class HeightWatchTest {
    private val watch = HeightWatch()

    @Test
    fun `the first measure of an item is not a change`() {
        assertEquals(0, watch.change("a", 300))
    }

    @Test
    fun `growth and shrink of the same item are reported against the previous measure`() {
        watch.change("a", 300)
        assertEquals(40, watch.change("a", 340))
        assertEquals(0, watch.change("a", 340))
        assertEquals(-25, watch.change("a", 315))
    }

    @Test
    fun `another item starts over from its own height`() {
        watch.change("a", 300)
        assertEquals(0, watch.change("b", 80))
        assertEquals(20, watch.change("b", 100))
    }
}
