package dev.huecoder.md3agent.system.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenChangesTest {
    @Test
    fun `identical screens have no changes`() {
        val screen = listOf(testElement(1, "Wi-Fi"), testElement(2, "Bluetooth"))

        assertTrue(ScreenChanges.between(screen, screen).isEmpty)
    }

    @Test
    fun `new and gone elements are listed`() {
        val before = listOf(testElement(1, "Wi-Fi"), testElement(2, "Bluetooth"))
        val after = listOf(testElement(1, "Wi-Fi"), testElement(2, "Hotspot"), testElement(3, "Connected", role = "Text"))

        val changes = ScreenChanges.between(before, after)

        assertEquals(listOf("Item \"Hotspot\"", "Text \"Connected\""), changes.added)
        assertEquals(listOf("Item \"Bluetooth\""), changes.removed)
        assertEquals("+ Item \"Hotspot\"\n+ Text \"Connected\"\n- Item \"Bluetooth\"", changes.describe())
    }

    @Test
    fun `duplicates are counted not collapsed`() {
        val before = listOf(testElement(1, "Row"), testElement(2, "Row"))
        val after = listOf(testElement(1, "Row"))

        assertEquals(listOf("Item \"Row\""), ScreenChanges.between(before, after).removed)
    }

    @Test
    fun `a long change list is cut with a count`() {
        val after = (1..40).map { testElement(it, "New $it") }

        val text = ScreenChanges.between(emptyList(), after).describe(limit = 5)

        assertEquals(6, text.lines().size)
        assertTrue(text.lines().last().contains("35 more new elements"))
    }

    @Test
    fun `the loop guard stays quiet while the screen changes`() {
        val guard = RepeatGuard()

        assertFalse(guard.record("tap Save", changedScreen = true))
        assertFalse(guard.record("tap Save", changedScreen = true))
        assertFalse(guard.record("tap Save", changedScreen = true))
    }

    @Test
    fun `the loop guard speaks up after the third identical action with no effect`() {
        val guard = RepeatGuard()

        assertFalse(guard.record("tap Save", changedScreen = false))
        assertFalse(guard.record("tap Save", changedScreen = false))
        assertTrue(guard.record("tap Save", changedScreen = false))
        assertTrue(guard.record("tap Save", changedScreen = false))
    }

    @Test
    fun `a different action or a change resets the loop guard`() {
        val guard = RepeatGuard()
        guard.record("tap Save", changedScreen = false)
        guard.record("tap Save", changedScreen = false)

        assertFalse(guard.record("tap Cancel", changedScreen = false))
        assertFalse(guard.record("tap Cancel", changedScreen = false))
        assertFalse(guard.record("tap Cancel", changedScreen = true))
        assertFalse(guard.record("tap Cancel", changedScreen = false))
    }
}
