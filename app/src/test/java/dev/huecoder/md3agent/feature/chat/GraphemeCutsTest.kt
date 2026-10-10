package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphemeCutsTest {
    private val cuts = GraphemeCuts()
    private val text = "a😀b😀😀é!"

    private fun splitsSurrogatePair(at: Int) =
        at in 1 until text.length && text[at - 1].isHighSurrogate() && text[at].isLowSurrogate()

    @Test
    fun `every reveal position lands outside surrogate pairs and never moves backwards`() {
        var previous = 0
        for (offset in 0..text.length) {
            val cut = cuts.floor(text, offset)
            assertTrue("cut $cut is past offset $offset", cut <= offset)
            assertFalse("cut $cut splits an emoji", splitsSurrogatePair(cut))
            assertTrue("cut moved back from $previous to $cut", cut >= previous)
            previous = cut
        }
    }

    @Test
    fun `an emoji appears whole once the reveal reaches its end`() {
        val emojiStart = text.indexOf("😀")
        assertEquals(emojiStart, cuts.floor(text, emojiStart + 1))
        assertEquals(emojiStart + 2, cuts.floor(text, emojiStart + 2))
    }

    @Test
    fun `out of range offsets clamp to the text`() {
        assertEquals(0, cuts.floor(text, -5))
        assertEquals(text.length, cuts.floor(text, text.length + 5))
    }

    @Test
    fun `walking a range yields contiguous pieces that keep emoji together`() {
        val pieces = mutableListOf<IntRange>()

        cuts.forEach(text, 0, text.length) { from, to -> pieces += from until to }

        assertEquals(0, pieces.first().first)
        assertEquals(text.length, pieces.last().last + 1)
        pieces.zipWithNext().forEach { (left, right) -> assertEquals(left.last + 1, right.first) }
        pieces.forEach { assertFalse("piece $it splits an emoji", splitsSurrogatePair(it.first)) }
    }

    @Test
    fun `walking stops at the range end`() {
        val pieces = mutableListOf<Pair<Int, Int>>()

        cuts.forEach("abcdef", 2, 4) { from, to -> pieces += from to to }

        assertEquals(listOf(2 to 3, 3 to 4), pieces)
    }
}
