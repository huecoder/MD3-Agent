package dev.huecoder.md3agent.ui.math

import org.junit.Assert.assertEquals
import org.junit.Test

class MathExtractorTest {
    private fun extract(source: String, accepts: (MathFormula) -> Boolean = { true }) = MathExtractor.extract(source, accepts)

    @Test
    fun `inline dollars become a slot`() {
        val result = extract("energy ${'$'}E=mc^2${'$'} here")
        assertEquals("energy MATHSLOT0X here", result.markdown)
        assertEquals(listOf(MathFormula("E=mc^2", isDisplay = false)), result.formulas)
    }

    @Test
    fun `display dollars span lines`() {
        val result = extract("a\n${'$'}${'$'}\n\\int_0^1 x\\,dx\n${'$'}${'$'}\nb")
        assertEquals("a\nMATHSLOT0X\nb", result.markdown)
        assertEquals(MathFormula("\\int_0^1 x\\,dx", isDisplay = true), result.formulas.single())
    }

    @Test
    fun `latex bracket delimiters`() {
        val result = extract("\\(a+b\\) and \\[c\\]")
        assertEquals("MATHSLOT0X and MATHSLOT1X", result.markdown)
        assertEquals(listOf(false, true), result.formulas.map { it.isDisplay })
    }

    @Test
    fun `currency is left alone`() {
        val text = "it costs ${'$'}5 and ${'$'}10 total"
        assertEquals(text, extract(text).markdown)
    }

    @Test
    fun `code is left alone`() {
        val text = "`${'$'}x${'$'}` and\n```\n${'$'}${'$'}y${'$'}${'$'}\n```\nand ${'$'}z${'$'}"
        val result = extract(text)
        assertEquals("`${'$'}x${'$'}` and\n```\n${'$'}${'$'}y${'$'}${'$'}\n```\nand MATHSLOT0X", result.markdown)
        assertEquals("z", result.formulas.single().latex)
    }

    @Test
    fun `unfinished formula stays literal`() {
        val text = "so ${'$'}${'$'}x^2 +"
        assertEquals(text, extract(text).markdown)
    }

    @Test
    fun `escaped dollar is not a delimiter`() {
        val text = "pay \\${'$'}5 or \\${'$'}6"
        assertEquals(text, extract(text).markdown)
    }

    @Test
    fun `rejected formulas keep their source`() {
        val result = extract("bad ${'$'}\\oops${'$'} ok ${'$'}x${'$'}") { it.latex != "\\oops" }
        assertEquals("bad ${'$'}\\oops${'$'} ok MATHSLOT0X", result.markdown)
    }
}
