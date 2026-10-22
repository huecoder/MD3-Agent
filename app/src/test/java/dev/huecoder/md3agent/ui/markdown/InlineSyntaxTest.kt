package dev.huecoder.md3agent.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineSyntaxTest {
    private val open = InlineSyntax.HIGHLIGHT_OPEN
    private val close = InlineSyntax.HIGHLIGHT_CLOSE

    private fun prepare(text: String) = InlineSyntax.prepare(text)

    @Test
    fun `double equals around words become highlight markers`() {
        assertEquals("a ${open}two words$close b", prepare("a ==two words== b").markdown)
    }

    @Test
    fun `comparisons and rules are left alone`() {
        assertEquals("if a == b then", prepare("if a == b then").markdown)
        assertEquals("Title\n=====", prepare("Title\n=====").markdown)
        assertEquals("x==y==z", prepare("x==y==z").markdown)
    }

    @Test
    fun `an unclosed highlight stays as typed`() {
        assertEquals("see ==this", prepare("see ==this").markdown)
    }

    @Test
    fun `code spans and fences are skipped`() {
        assertEquals("`==a==` and ${open}b$close", prepare("`==a==` and ==b==").markdown)
        assertEquals("```\n==a==\n```\n${open}b$close", prepare("```\n==a==\n```\n==b==").markdown)
    }

    @Test
    fun `highlights may hold other markup`() {
        assertEquals("${open}**bold** word$close", prepare("==**bold** word==").markdown)
    }

    @Test
    fun `footnotes are numbered by first reference and their definitions leave the text`() {
        val prepared = prepare("One[^b] and two[^a].\n\n[^a]: Second.\n[^b]: First,\n    continued.\n")

        assertEquals("One${InlineSyntax.footnoteMarker(1)} and two${InlineSyntax.footnoteMarker(2)}.\n\n", prepared.markdown)
        assertEquals(listOf(Footnote(1, "First,\ncontinued."), Footnote(2, "Second.")), prepared.footnotes)
    }

    @Test
    fun `a reference without a definition stays literal`() {
        val prepared = prepare("Lost[^x] note")

        assertEquals("Lost[^x] note", prepared.markdown)
        assertEquals(emptyList<Footnote>(), prepared.footnotes)
    }

    @Test
    fun `a definition nobody references is still listed`() {
        val prepared = prepare("Text\n\n[^n]: Orphan")

        assertEquals(listOf(Footnote(1, "Orphan")), prepared.footnotes)
    }

    @Test
    fun `marker characters are told apart`() {
        assertEquals(3, InlineSyntax.footnoteNumber(InlineSyntax.footnoteMarker(3)))
        assertEquals(null, InlineSyntax.footnoteNumber('a'))
        assertEquals(true, InlineSyntax.isMarker(open))
        assertEquals(false, InlineSyntax.isMarker('='))
    }
}
