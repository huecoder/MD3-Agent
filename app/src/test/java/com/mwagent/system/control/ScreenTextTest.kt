package com.mwagent.system.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTextTest {
    private val elements = listOf(
        testElement(1, "Settings", role = "Text", clickable = false, box = ScreenBox(100, 100, 300, 180)),
        testElement(2, "Wi-Fi", role = "Item", box = ScreenBox(0, 300, 1080, 400)),
        testElement(3, "", role = "List", clickable = false, scrollable = true, scrollMoves = "↓↑", box = ScreenBox(0, 200, 1080, 2300)),
        testElement(4, "Bluetooth", partial = true, box = ScreenBox(0, 2250, 1080, 2400)),
    )

    private fun describe(find: ElementSelector? = null, hidden: Int = 0, cutouts: List<ScreenBox> = emptyList(), detailed: Boolean = false) =
        ScreenText.describe("com.android.settings", 1080, 2400, cutouts, elements, hidden, detailed, find)

    @Test
    fun `the header names the app and the screen`() {
        assertTrue(describe().startsWith("App: com.android.settings, screen 1080x2400"))
    }

    @Test
    fun `cutouts are listed in the header`() {
        val text = describe(cutouts = listOf(ScreenBox(400, 0, 680, 90)))

        assertTrue(text.lines().first().contains("cutouts 400,0-680,90"))
    }

    @Test
    fun `each element shows role, label, traits and its center`() {
        val lines = describe().lines()

        assertEquals("[2] Item \"Wi-Fi\" (tap) @540,350", lines[2])
        assertEquals("[1] Text \"Settings\" @200,140", lines[1])
    }

    @Test
    fun `lists show which way they can scroll`() {
        assertTrue(describe().contains("[3] List (scroll↓↑) @540,1250"))
    }

    @Test
    fun `a partly visible element says so`() {
        assertTrue(describe().contains("[4] Item \"Bluetooth\" (tap, partly visible)"))
    }

    @Test
    fun `hidden elements are announced`() {
        assertTrue(describe(hidden = 12).contains("+12 more elements are not shown"))
        assertFalse(describe(hidden = 0).contains("more elements"))
    }

    @Test
    fun `find lists only the matches with their exact bounds`() {
        val text = describe(find = ElementSelector(text = "wi-fi"), hidden = 3)

        assertTrue(text.contains("Found 1 of 7 elements for text \"wi-fi\""))
        assertTrue(text.contains("[2] Item \"Wi-Fi\" (tap) @540,350 bounds=[0,300][1080,400]"))
        assertFalse(text.contains("Bluetooth"))
        assertFalse(text.contains("more elements"))
    }

    @Test
    fun `find that matches nothing says where to look next`() {
        val text = describe(find = ElementSelector(text = "nothing here"))

        assertTrue(text.contains("Found 0 of 4"))
        assertTrue(text.contains("Nothing matches"))
    }

    @Test
    fun `an empty screen asks for coordinates`() {
        val text = ScreenText.describe("com.game", 1080, 2400, emptyList(), emptyList(), 0)

        assertTrue(text.contains("No readable elements"))
    }
}
