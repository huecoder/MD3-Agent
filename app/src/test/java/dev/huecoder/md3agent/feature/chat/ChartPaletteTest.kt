package dev.huecoder.md3agent.feature.chat

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartPaletteTest {
    private val seed = Color(0xFF3F51B5)

    @Test
    fun `every slice gets its own colour and the first one is the seed`() {
        val palette = distinctColors(seed, count = 12)

        assertEquals(12, palette.toSet().size)
        assertEquals(seed, palette.first())
    }

    @Test
    fun `a grey seed still yields different colours`() {
        assertEquals(6, distinctColors(Color(0xFF808080), count = 6).toSet().size)
    }

    @Test
    fun `an empty request still yields one colour`() {
        assertEquals(listOf(seed), distinctColors(seed, count = 0))
    }
}
