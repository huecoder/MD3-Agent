package dev.huecoder.md3agent.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunkerTest {
    @Test
    fun `holds a sentence until it ends`() {
        val chunker = SpeechChunker()
        assertTrue(chunker.ready("The weather today is sunny and wa").isEmpty())
        assertEquals(listOf("The weather today is sunny and warm."), chunker.ready("The weather today is sunny and warm. Tomor"))
        assertEquals(listOf("Tomorrow it rains."), chunker.rest("The weather today is sunny and warm. Tomorrow it rains."))
    }

    @Test
    fun `waits for a code block to close and skips it`() {
        val chunker = SpeechChunker()
        val open = "Run this command please:\n```\nls -la. /tmp\n"
        assertEquals(listOf("Run this command please:"), chunker.ready(open))
        assertTrue(chunker.ready(open + "rm x.\n").isEmpty())
        assertEquals(emptyList<String>(), chunker.ready(open + "rm x.\n```\n"))
        assertEquals(listOf("Done, that is all."), chunker.rest(open + "rm x.\n```\nDone, that is all."))
    }

    @Test
    fun `markdown is read as plain text`() {
        assertEquals(
            "Title Look at the docs for bold details and run.",
            SpeakableText.of("## Title\n- Look at [the docs](https://x.io) for **bold** details and `run`."),
        )
    }

    @Test
    fun `long text without punctuation is split on spaces`() {
        val chunks = SpeechChunker().rest("word ".repeat(200))
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 400 })
    }
}
