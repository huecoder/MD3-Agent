package dev.huecoder.md3agent.ui.markdown

import dev.huecoder.md3agent.ui.pictures.PictureAlign
import dev.huecoder.md3agent.ui.pictures.PictureGrouping
import dev.huecoder.md3agent.ui.pictures.PictureOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {
    private fun split(text: String) = MarkdownBlocks.split(text)

    private fun List<MarkdownBlock>.shape(): List<String> = map {
        when (it) {
            is MarkdownBlock.Text -> "text"
            is MarkdownBlock.Pictures -> "pictures:${it.pictures.size}"
            is MarkdownBlock.Callout -> "callout:${it.type}"
            is MarkdownBlock.Details -> "details"
        }
    }

    @Test
    fun `text without images stays one untouched block`() {
        val text = "# Title\n\nSome **text**\n"

        assertEquals(listOf(MarkdownBlock.Text(text, 0)), split(text))
    }

    @Test
    fun `an image on its own line splits the text around it`() {
        val text = "Intro\n![a](u)\nOutro"

        val blocks = split(text)

        assertEquals(listOf("text", "pictures:1", "text"), blocks.shape())
        assertEquals("Intro\n", (blocks[0] as MarkdownBlock.Text).markdown)
        assertEquals("Outro", (blocks[2] as MarkdownBlock.Text).markdown)
        assertEquals(text.indexOf("Outro"), blocks[2].start)
    }

    @Test
    fun `images on neighbouring lines form one group`() {
        val blocks = split("![a](u1)\n![b](u2)\n![c](u3)\n\ntext")

        assertEquals(listOf("pictures:3", "text"), blocks.shape())
    }

    @Test
    fun `a blank line keeps images apart`() {
        val blocks = split("![a](u1)\n\n![b](u2)")

        assertEquals(listOf("pictures:1", "pictures:1"), blocks.shape())
    }

    @Test
    fun `several images on one line are one group`() {
        assertEquals(listOf("pictures:3"), split("![a](u1) ![b](u2) ![c](u3)").shape())
    }

    @Test
    fun `group options come from the first picture that has them`() {
        val block = split("![a](u1 \"right\")\n![b](u2 \"slider 50%\")") .single() as MarkdownBlock.Pictures

        assertEquals(PictureOptions(PictureAlign.END, 0.5f, PictureGrouping.SLIDER), block.options)
    }

    @Test
    fun `images inside code fences are code`() {
        val text = "```\n![a](u)\n```\n~~~md\n![b](u)\n~~~\n![c](u)"

        val blocks = split(text)

        assertEquals(listOf("text", "pictures:1"), blocks.shape())
        assertEquals("c", (blocks[1] as MarkdownBlock.Pictures).pictures.single().description)
    }

    @Test
    fun `an unclosed fence swallows the rest`() {
        assertEquals(listOf("text"), split("```\n![a](u)\n![b](u)").shape())
    }

    @Test
    fun `images in lists quotes and sentences are left to the renderer`() {
        val text = "- ![a](u)\n> ![b](u)\nLook ![c](u) here\n  ![d](u)"

        assertEquals(listOf("text"), split(text).shape())
    }

    @Test
    fun `an image typed halfway stays text until it is complete`() {
        assertEquals(listOf("text"), split("Intro\n![a](https://exam").shape())
        assertEquals(listOf("text", "pictures:1"), split("Intro\n![a](https://example.com/a.png)").shape())
    }

    @Test
    fun `windows line endings are understood`() {
        assertEquals(listOf("text", "pictures:2", "text"), split("a\r\n![x](u1)\r\n![y](u2)\r\nb").shape())
    }

    @Test
    fun `block offsets point into the original text`() {
        val text = "one\n![a](u)\ntwo\n![b](u)\nthree"

        val blocks = split(text)

        blocks.forEach { block ->
            if (block is MarkdownBlock.Text) assertTrue(text.startsWith(block.markdown, block.start))
        }
        assertEquals(listOf("text", "pictures:1", "text", "pictures:1", "text"), blocks.shape())
    }

    @Test
    fun `text made only of blank lines is dropped`() {
        assertEquals(listOf("pictures:1"), split("\n\n![a](u)\n\n").shape())
        assertEquals(listOf("pictures:1", "pictures:1"), split("![a](u)\n\n\n![b](u)\n").shape())
    }

    @Test
    fun `details keep the summary and split their body`() {
        val text = "Before\n<details open>\n<summary>**Open** me</summary>\n\nInside\n![a](u)\n\n</details>\nAfter"

        val blocks = split(text)

        assertEquals(listOf("text", "details", "text"), blocks.shape())
        val details = blocks[1] as MarkdownBlock.Details
        assertEquals("Open me", details.summary)
        assertEquals(true, details.isOpen)
        assertEquals(listOf("text", "pictures:1"), details.body.shape())
        assertEquals("After", (blocks[2] as MarkdownBlock.Text).markdown.trim())
    }

    @Test
    fun `nested details and an unclosed one are understood`() {
        val nested = split("<details><summary>A</summary>\n<details><summary>B</summary>\nx\n</details>\ny\n</details>").single() as MarkdownBlock.Details

        assertEquals(listOf("details", "text"), nested.body.shape())
        val open = split("<details>\n<summary>Streaming").single() as MarkdownBlock.Details
        assertEquals("Streaming", open.summary)
        assertEquals(emptyList<MarkdownBlock>(), open.body)
    }

    @Test
    fun `a quote that starts with a marker is a callout`() {
        val blocks = split("> [!tip]- Fold *me*\n> first\n>\n> - item\n\nTail")

        assertEquals(listOf("callout:tip", "text"), blocks.shape())
        val callout = blocks[0] as MarkdownBlock.Callout
        assertEquals("Fold *me*", callout.title)
        assertEquals(CalloutFold.CLOSED, callout.fold)
        assertEquals(CalloutKind.TIP, callout.kind)
        assertEquals("first\n\n- item", (callout.body.single() as MarkdownBlock.Text).markdown)
    }

    @Test
    fun `github alerts and unknown types are callouts without a title`() {
        val note = split("> [!NOTE]\n> plain").single() as MarkdownBlock.Callout
        val odd = split("> [!whatever]+\n> x").single() as MarkdownBlock.Callout

        assertEquals(null, note.title)
        assertEquals(CalloutFold.NONE, note.fold)
        assertEquals(null, odd.kind)
        assertEquals(CalloutFold.OPEN, odd.fold)
    }

    @Test
    fun `callouts inside code or in the middle of a quote stay plain`() {
        assertEquals(listOf("text"), split("```\n> [!tip]\n> x\n```").shape())
        assertEquals(listOf("text"), split("> intro\n> [!tip]\n> x").shape())
        assertEquals(listOf("text"), split("> [!tip typed halfway").shape())
    }

    @Test
    fun `offsets of container bodies point near their place in the text`() {
        val text = "intro\n\n<details><summary>S</summary>\nbody\n</details>"

        val details = split(text)[1] as MarkdownBlock.Details
        val body = details.body.single() as MarkdownBlock.Text

        assertEquals(text.indexOf("\nbody"), body.start)
    }
}
