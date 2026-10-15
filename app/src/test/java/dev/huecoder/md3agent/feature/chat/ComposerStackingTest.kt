package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerStackingTest {
    @Test
    fun `an empty field is never stacked`() {
        assertFalse(ComposerStacking.isStacked(wrappedAt = null, text = ""))
        assertNull(ComposerStacking.settle(wrappedAt = 40, text = ""))
    }

    @Test
    fun `a field that wrapped after only a few characters shrinks once it is emptied`() {
        assertNull(ComposerStacking.settle(wrappedAt = 3, text = ""))
        assertNull(ComposerStacking.settle(wrappedAt = 6, text = ""))
        assertNull(ComposerStacking.settle(wrappedAt = 1, text = ""))
    }

    @Test
    fun `a newline stacks the field without remembering a wrap length`() {
        val text = "ab\n"
        val wrappedAt = ComposerStacking.wrappedAfter(null, text, lineCount = 2)

        assertNull(wrappedAt)
        assertTrue(ComposerStacking.isStacked(wrappedAt, text))
        assertFalse(ComposerStacking.isStacked(wrappedAt, "ab"))
    }

    @Test
    fun `a wrapped line stacks the field and remembers where it wrapped`() {
        val text = "x".repeat(42)

        val wrappedAt = ComposerStacking.wrappedAfter(null, text, lineCount = 2)

        assertEquals(42, wrappedAt)
        assertTrue(ComposerStacking.isStacked(wrappedAt, text))
    }

    @Test
    fun `one line of text does not stack the field`() {
        assertNull(ComposerStacking.wrappedAfter(null, "hello", lineCount = 1))
    }

    @Test
    fun `the remembered wrap length is kept while more text is typed`() {
        assertEquals(42, ComposerStacking.wrappedAfter(42, "x".repeat(60), lineCount = 3))
    }

    @Test
    fun `deleting a little keeps the field stacked, deleting enough shrinks it`() {
        assertEquals(42, ComposerStacking.settle(42, "x".repeat(40)))
        assertEquals(42, ComposerStacking.settle(42, "x".repeat(36)))
        assertNull(ComposerStacking.settle(42, "x".repeat(35)))
    }
}
