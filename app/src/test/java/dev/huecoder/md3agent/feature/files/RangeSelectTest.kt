package dev.huecoder.md3agent.feature.files

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeSelectTest {
    private val ordered = listOf("a", "b", "c", "d", "e")
    private val range = RangeSelect()

    @Test
    fun `dragging from the anchor selects everything in between`() {
        assertEquals(setOf("b", "c", "d"), range.extend(setOf("b"), ordered, anchor = "b", current = "d"))
    }

    @Test
    fun `dragging backwards works the same`() {
        assertEquals(setOf("b", "c", "d"), range.extend(setOf("d"), ordered, anchor = "d", current = "b"))
    }

    @Test
    fun `shrinking the drag drops the items it no longer covers`() {
        val start = setOf("b")
        range.extend(start, ordered, anchor = "b", current = "e")

        assertEquals(setOf("b", "c"), range.extend(setOf("b", "c", "d", "e"), ordered, anchor = "b", current = "c"))
    }

    @Test
    fun `what was selected before the drag stays selected`() {
        val before = setOf("a", "e")

        assertEquals(setOf("a", "e", "b", "c"), range.extend(before, ordered, anchor = "b", current = "c"))
    }

    @Test
    fun `a new drag starts from the selection as it is after the last one`() {
        range.extend(setOf("a"), ordered, anchor = "b", current = "c")
        range.end()

        assertEquals(setOf("b", "c", "d"), range.extend(setOf("b", "c"), ordered, anchor = "d", current = "d"))
    }

    @Test
    fun `an unknown key changes nothing`() {
        assertEquals(setOf("a"), range.extend(setOf("a"), ordered, anchor = "a", current = "zzz"))
    }
}
