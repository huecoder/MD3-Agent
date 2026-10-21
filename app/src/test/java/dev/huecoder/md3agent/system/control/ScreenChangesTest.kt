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
    fun `new, changed and gone elements are listed with new numbers`() {
        val before = listOf(testElement(1, "Wi-Fi"), testElement(2, "Bluetooth"))
        val after = listOf(testElement(1, "Wi-Fi"), testElement(2, "Hotspot"), testElement(3, "Connected", role = "Text", clickable = false))

        val changes = ScreenChanges.between(before, after)

        assertEquals(listOf("Hotspot", "Connected"), changes.added.map { it.label })
        assertEquals(listOf("Bluetooth"), changes.removed.map { it.label })
        val lines = changes.describe().lines()
        assertTrue(lines[0].startsWith("+ [2] Item \"Hotspot\""))
        assertTrue(lines[1].startsWith("+ [3] Text \"Connected\""))
        assertEquals("- Item \"Bluetooth\"", lines[2])
    }

    @Test
    fun `a switched state is a change not a new element`() {
        val off = testElement(1, "Wi-Fi", role = "Switch").copy(checked = false)
        val on = off.copy(checked = true)

        val changes = ScreenChanges.between(listOf(off), listOf(on))

        assertTrue(changes.added.isEmpty() && changes.removed.isEmpty())
        assertTrue(changes.describe().startsWith("~ [1] Switch \"Wi-Fi\" (tap, checked)"))
    }

    @Test
    fun `duplicates are counted not collapsed`() {
        val before = listOf(testElement(1, "Row"), testElement(2, "Row"))
        val after = listOf(testElement(1, "Row"))

        assertEquals(1, ScreenChanges.between(before, after).removed.size)
    }

    @Test
    fun `a long change list is cut with a count`() {
        val after = (1..40).map { testElement(it, "New $it") }

        val lines = ScreenChanges.between(emptyList(), after).describe(limit = 5).lines()

        assertEquals(6, lines.size)
        assertEquals("+35 changed", lines.last())
    }

    @Test
    fun `moved numbers are announced only when something else changed`() {
        val before = listOf(testElement(1, "A"), testElement(2, "B"))
        val shifted = listOf(testElement(1, "New"), testElement(2, "A"), testElement(3, "B"))

        assertTrue(ScreenChanges.between(before, shifted).describe().endsWith(ScreenChanges.RENUMBERED_NOTE))
        assertEquals("", ScreenChanges.between(before, listOf(testElement(2, "A"), testElement(3, "B"))).describe())
    }

    @Test
    fun `a mostly new screen counts as a move to another screen`() {
        val before = (1..10).map { testElement(it, "Old $it") }
        val after = (1..10).map { testElement(it, "New $it") }
        val tweak = before.take(9) + testElement(10, "Other")

        assertTrue(ScreenChanges.between(before, after).replacesScreen(before.size, after.size))
        assertFalse(ScreenChanges.between(before, tweak).replacesScreen(before.size, tweak.size))
        assertFalse(ScreenChanges.between(before.take(3), after.take(3)).replacesScreen(3, 3))
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
