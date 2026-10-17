package dev.huecoder.md3agent.system.notifications

import dev.huecoder.md3agent.data.settings.ChipContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipTextTest {
    private val units = ChipUnits(second = "s", minute = "m", hour = "h", thousand = "k", million = "M", decimal = '.')

    private fun seconds(value: Long) = value * 1_000

    @Test
    fun `time keeps seconds only in the first minute when it shares the chip`() {
        assertEquals("45s", ChipText.duration(seconds(45), units, compact = true))
        assertEquals("2m", ChipText.duration(seconds(125), units, compact = true))
        assertEquals("1h", ChipText.duration(seconds(3_700), units, compact = true))
    }

    @Test
    fun `time alone shows the smaller unit too`() {
        assertEquals("2m05s", ChipText.duration(seconds(125), units, compact = false))
        assertEquals("1h01m", ChipText.duration(seconds(3_700), units, compact = false))
    }

    @Test
    fun `tokens are abbreviated`() {
        assertEquals("42", ChipText.tokens(42, units))
        assertEquals("850", ChipText.tokens(857, units))
        assertEquals("1.2k", ChipText.tokens(1_234, units))
        assertEquals("1k", ChipText.tokens(1_000, units))
        assertEquals("12k", ChipText.tokens(12_900, units))
        assertEquals("1.5M", ChipText.tokens(1_500_000, units))
    }

    @Test
    fun `time and tokens are split by a bar and fit the chip`() {
        assertEquals("2m|1.2k", ChipText.working(ChipContent.TIME_AND_TOKENS, seconds(125), 1_234, units))
        assertEquals("45s|1k", ChipText.working(ChipContent.TIME_AND_TOKENS, seconds(45), 1_234, units))
        assertEquals("12s", ChipText.working(ChipContent.TIME_AND_TOKENS, seconds(12), 0, units))
        listOf(1L to 5, 59L to 9_999, 61L to 120_000, 3_700L to 1_500_000).forEach { (elapsed, tokens) ->
            val text = ChipText.working(ChipContent.TIME_AND_TOKENS, seconds(elapsed), tokens, units)
            assertTrue(text, text.length <= ChipText.MAX_CHARS)
        }
    }

    @Test
    fun `units come from the locale`() {
        val russian = ChipUnits(second = "с", minute = "м", hour = "ч", thousand = "к", million = "М", decimal = ',')

        assertEquals("2м|1,2к", ChipText.working(ChipContent.TIME_AND_TOKENS, seconds(125), 1_234, russian))
    }
}
