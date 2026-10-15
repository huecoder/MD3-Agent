package dev.huecoder.md3agent.system.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

fun testElement(
    number: Int,
    label: String,
    role: String = "Item",
    viewId: String? = null,
    clickable: Boolean = true,
    editable: Boolean = false,
    scrollable: Boolean = false,
    partial: Boolean = false,
    scrollMoves: String = "",
    box: ScreenBox = ScreenBox(0, number * 100, 1080, number * 100 + 80),
) = ScreenElement(
    number = number,
    role = role,
    label = label,
    bounds = box,
    clickable = clickable,
    editable = editable,
    scrollable = scrollable,
    checked = null,
    focused = false,
    viewId = viewId,
    className = "android.widget.$role",
    partial = partial,
    scrollMoves = scrollMoves,
)

class ElementPickerTest {
    private val elements = listOf(
        testElement(1, "Network & internet", viewId = "title"),
        testElement(2, "Wi-Fi", viewId = "title"),
        testElement(3, "Wi-Fi calling", viewId = "title"),
        testElement(4, "Search settings", role = "Input", editable = true, viewId = "search_bar"),
        testElement(5, "Back · Navigate up", role = "Button", viewId = "up"),
    )

    @Test
    fun `text matches any part of the label ignoring case`() {
        val picked = ElementPicker.pick(ElementSelector(text = "network"), elements)

        assertEquals(Pick.Found(elements[0]), picked)
    }

    @Test
    fun `exact text must equal the label or one part of it`() {
        assertEquals(Pick.Found(elements[4]), ElementPicker.pick(ElementSelector(text = "navigate up", exact = true), elements))
        assertEquals(Pick.Missing(0), ElementPicker.pick(ElementSelector(text = "Wi", exact = true), elements))
    }

    @Test
    fun `an exact label wins over longer labels that merely contain it`() {
        assertEquals(Pick.Found(elements[1]), ElementPicker.pick(ElementSelector(text = "wi-fi"), elements))
    }

    @Test
    fun `several equal matches are ambiguous and listed`() {
        val twins = listOf(testElement(1, "Save"), testElement(2, "Save"), testElement(3, "Cancel"))

        val picked = ElementPicker.pick(ElementSelector(text = "save"), twins)

        assertEquals(Pick.Ambiguous(listOf(twins[0], twins[1])), picked)
    }

    @Test
    fun `an index picks among the matches counting from one`() {
        val twins = listOf(testElement(1, "Save"), testElement(2, "Save"), testElement(3, "Cancel"))

        assertEquals(Pick.Found(twins[1]), ElementPicker.pick(ElementSelector(text = "save", index = 2), twins))
        assertEquals(Pick.Missing(2), ElementPicker.pick(ElementSelector(text = "save", index = 3), twins))
    }

    @Test
    fun `id accepts the short or the full resource name`() {
        assertEquals(Pick.Found(elements[3]), ElementPicker.pick(ElementSelector(id = "search_bar"), elements))
        assertEquals(Pick.Found(elements[3]), ElementPicker.pick(ElementSelector(id = "com.android.settings:id/search_bar"), elements))
    }

    @Test
    fun `role narrows the match and combines with text`() {
        assertEquals(Pick.Found(elements[3]), ElementPicker.pick(ElementSelector(role = "input"), elements))
        assertEquals(Pick.Found(elements[3]), ElementPicker.pick(ElementSelector(text = "settings", role = "Input"), elements))
        assertEquals(Pick.Missing(0), ElementPicker.pick(ElementSelector(text = "Wi-Fi", role = "Button"), elements))
    }

    @Test
    fun `nothing matches reports zero`() {
        assertEquals(Pick.Missing(0), ElementPicker.pick(ElementSelector(text = "bluetooth"), elements))
    }

    @Test
    fun `candidates are capped`() {
        val many = (1..20).map { testElement(it, "Row $it") }

        val picked = ElementPicker.pick(ElementSelector(text = "row"), many) as Pick.Ambiguous

        assertEquals(ElementPicker.MAX_CANDIDATES, picked.candidates.size)
    }

    @Test
    fun `a selector describes itself for messages`() {
        assertEquals("text \"Wi-Fi\" role Button #2", ElementSelector(text = "Wi-Fi", role = "Button", index = 2).describe())
        assertTrue(ElementSelector().isBlank)
    }
}
