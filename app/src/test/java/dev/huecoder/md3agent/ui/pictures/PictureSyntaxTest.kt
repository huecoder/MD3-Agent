package dev.huecoder.md3agent.ui.pictures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PictureSyntaxTest {
    private fun options(title: String?) = PictureOptions.parse(title)

    @Test
    fun `a plain image line gives the url and the description`() {
        val pictures = PictureSyntax.line("![Obsidian](https://example.com/a.png)")!!

        assertEquals(listOf(Picture("https://example.com/a.png", "Obsidian")), pictures)
    }

    @Test
    fun `several images on one line are all found`() {
        val pictures = PictureSyntax.line("![a](u1) ![b](u2)  ![c](u3)")!!

        assertEquals(listOf("u1", "u2", "u3"), pictures.map { it.url })
        assertEquals(listOf("a", "b", "c"), pictures.map { it.description })
    }

    @Test
    fun `text around an image makes the line something else`() {
        assertNull(PictureSyntax.line("Look: ![a](u)"))
        assertNull(PictureSyntax.line("![a](u) caption"))
        assertNull(PictureSyntax.line(""))
        assertNull(PictureSyntax.line("   "))
        assertNull(PictureSyntax.line("![broken](u"))
        assertNull(PictureSyntax.line("![](  )"))
    }

    @Test
    fun `indented lines belong to lists and code and are left alone`() {
        assertNotNull(PictureSyntax.line(" ![a](u)"))
        assertNull(PictureSyntax.line("  ![a](u)"))
        assertNull(PictureSyntax.line("    ![a](u)"))
        assertNull(PictureSyntax.line("- ![a](u)"))
        assertNull(PictureSyntax.line("> ![a](u)"))
    }

    @Test
    fun `balanced parentheses and angle brackets stay in the url`() {
        assertEquals("https://en.wikipedia.org/wiki/File:Foo_(bar).jpg", PictureSyntax.line("![x](https://en.wikipedia.org/wiki/File:Foo_(bar).jpg)")!!.single().url)
        assertEquals("a b.png", PictureSyntax.line("![x](<a b.png>)")!!.single().url)
    }

    @Test
    fun `a title carries the options in any quote style`() {
        assertEquals(PictureAlign.END, PictureSyntax.line("![x](u \"right\")")!!.single().options.align)
        assertEquals(PictureAlign.CENTER, PictureSyntax.line("![x](u 'center')")!!.single().options.align)
        assertEquals(PictureAlign.START, PictureSyntax.line("![x](u (left))")!!.single().options.align)
    }

    @Test
    fun `escaped brackets in the description are unescaped`() {
        assertEquals("a [b] c", PictureSyntax.line("![a \\[b\\] c](u)")!!.single().description)
        assertEquals("a [b] c", PictureSyntax.line("![a [b] c](u)")!!.single().description)
    }

    @Test
    fun `single takes exactly one image`() {
        assertEquals("u", PictureSyntax.single("![x](u)")!!.url)
        assertNull(PictureSyntax.single("![x](u) tail"))
        assertNull(PictureSyntax.single("[x](u)"))
    }

    @Test
    fun `options understand position size and grouping words`() {
        assertEquals(PictureOptions(PictureAlign.END, 0.5f, null), options("right 50%"))
        assertEquals(PictureOptions(PictureAlign.CENTER, 0.6f, null), options("Center, medium"))
        assertEquals(PictureOptions(null, 0.4f, PictureGrouping.SLIDER), options("small slider"))
        assertEquals(PictureOptions(null, 1f, PictureGrouping.ALBUM), options("grid full"))
        assertEquals(PictureOptions(null, null, PictureGrouping.SLIDER), options("row"))
    }

    @Test
    fun `options accept the key equals value spelling`() {
        assertEquals(PictureOptions(PictureAlign.END, 0.3f, PictureGrouping.ALBUM), options("align=right width=30% layout=album"))
        assertNull(options("colour=red"))
    }

    @Test
    fun `percentages are kept in a sane range`() {
        assertEquals(PictureOptions.MIN_SHARE, options("1%")!!.widthShare)
        assertEquals(1f, options("250%")!!.widthShare)
    }

    @Test
    fun `a title with any unknown word is just a title`() {
        assertNull(options("Left side view"))
        assertNull(options("Pixel 10 Pro, right"))
        assertNull(options("right 50"))
        assertNull(options(""))
        assertNull(options(null))
        assertEquals(PictureOptions.NONE, PictureSyntax.line("![x](u \"Left side view\")")!!.single().options)
    }

    @Test
    fun `the first word of a kind wins`() {
        assertEquals(PictureAlign.END, options("right left")!!.align)
        assertEquals(0.4f, options("small large")!!.widthShare)
    }
}
