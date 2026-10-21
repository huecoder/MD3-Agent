package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableColumnsTest {
    @Test
    fun `columns that fit are stretched in proportion to fill the width`() {
        val widths = TableColumns.widths(minimums = listOf(50, 50), preferred = listOf(100, 300), available = 800)

        assertEquals(listOf(200, 600), widths)
    }

    @Test
    fun `the rounding remainder goes to the last column so the width is exact`() {
        val widths = TableColumns.widths(minimums = listOf(10, 10, 10), preferred = listOf(100, 100, 100), available = 1000)

        assertEquals(1000, widths.sum())
        assertEquals(listOf(333, 333, 334), widths)
    }

    @Test
    fun `wide columns shrink toward their minimums when everything can still fit`() {
        val widths = TableColumns.widths(minimums = listOf(100, 100), preferred = listOf(500, 300), available = 600)

        assertEquals(600, widths.sum())
        assertTrue(widths[0] in 100..500)
        assertTrue(widths[1] in 100..300)
        assertTrue("the column that wants more keeps more", widths[0] > widths[1])
    }

    @Test
    fun `a table that cannot fit even at minimum widths keeps its preferred widths to scroll`() {
        val widths = TableColumns.widths(minimums = listOf(300, 300, 300), preferred = listOf(400, 500, 600), available = 800)

        assertEquals(listOf(400, 500, 600), widths)
    }

    @Test
    fun `no columns or no room returns the preferred widths untouched`() {
        assertEquals(emptyList<Int>(), TableColumns.widths(emptyList(), emptyList(), 500))
        assertEquals(listOf(120, 80), TableColumns.widths(listOf(50, 50), listOf(120, 80), 0))
    }

    @Test
    fun `columns of equal minimum and preferred width stay as they are when space is short`() {
        val widths = TableColumns.widths(minimums = listOf(200, 200), preferred = listOf(200, 200), available = 300)

        assertEquals(listOf(200, 200), widths)
    }
}
