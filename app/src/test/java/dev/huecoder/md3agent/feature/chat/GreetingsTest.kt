package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class GreetingsTest {
    @Test
    fun `day parts change at five, twelve, eighteen and twenty three`() {
        assertEquals(DayPart.NIGHT, DayPart.at(0))
        assertEquals(DayPart.NIGHT, DayPart.at(4))
        assertEquals(DayPart.MORNING, DayPart.at(5))
        assertEquals(DayPart.MORNING, DayPart.at(11))
        assertEquals(DayPart.DAY, DayPart.at(12))
        assertEquals(DayPart.DAY, DayPart.at(17))
        assertEquals(DayPart.EVENING, DayPart.at(18))
        assertEquals(DayPart.EVENING, DayPart.at(22))
        assertEquals(DayPart.NIGHT, DayPart.at(23))
    }

    @Test
    fun `every seed lands inside the pool, negative ones too`() {
        val timed = listOf("a", "b")
        val common = listOf("c")

        val picked = listOf(Int.MIN_VALUE, -7, -1, 0, 1, 2, 3, Int.MAX_VALUE).map { Greetings.pick(timed, common, it) }

        assertTrue(picked.all { it in timed + common })
        assertEquals(setOf("a", "b", "c"), picked.toSet())
    }

    @Test
    fun `the same seed always gives the same greeting`() {
        val timed = listOf("a", "b", "c")

        assertEquals(Greetings.pick(timed, listOf("d"), 12345), Greetings.pick(timed, listOf("d"), 12345))
    }

    @Test
    fun `both languages define the same greeting sets`() {
        val english = greetingsIn("values")
        val russian = greetingsIn("values-ru")

        assertEquals(EXPECTED_SETS, english.keys)
        assertEquals(english.keys, russian.keys)
    }

    @Test
    fun `each set has enough variations without repeats`() {
        listOf(greetingsIn("values"), greetingsIn("values-ru")).forEach { language ->
            language.forEach { (name, items) ->
                assertTrue("$name has ${items.size} greetings", items.size >= MIN_VARIATIONS)
                assertEquals("$name repeats a greeting", items.size, items.toSet().size)
            }
        }
    }

    @Test
    fun `greetings stay short enough for two lines of the big title`() {
        listOf(greetingsIn("values"), greetingsIn("values-ru")).forEach { language ->
            language.forEach { (name, items) ->
                items.forEach { assertTrue("$name: \"$it\" is ${it.length} characters", it.length <= MAX_LENGTH) }
            }
        }
    }

    private fun greetingsIn(folder: String): Map<String, List<String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/greetings.xml"))
        val arrays = document.getElementsByTagName("string-array")
        return (0 until arrays.length).associate { index ->
            val array = arrays.item(index)
            val items = array.childNodes
            val texts = (0 until items.length).map { items.item(it) }.filter { it.nodeName == "item" }.map { it.textContent.replace("\\'", "'") }
            array.attributes.getNamedItem("name").nodeValue to texts
        }
    }

    private companion object {
        val EXPECTED_SETS = setOf("greetings_any", "greetings_morning", "greetings_day", "greetings_evening", "greetings_night")
        const val MIN_VARIATIONS = 8
        const val MAX_LENGTH = 34
    }
}
