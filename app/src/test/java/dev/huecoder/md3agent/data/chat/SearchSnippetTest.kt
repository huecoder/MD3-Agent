package dev.huecoder.md3agent.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSnippetTest {
    private val long = "Yesterday we talked about many things. The weather in Moscow was cold, then we planned a trip to the sea and argued about trains for a while."

    @Test
    fun `match in a long message is centered and marked`() {
        val snippet = SearchSnippet.around(long, "moscow", width = 40)
        val match = snippet.match!!
        assertEquals("Moscow", snippet.text.substring(match.first, match.last + 1))
        assertTrue(snippet.text.startsWith("…"))
        assertTrue(snippet.text.endsWith("…"))
        assertTrue(snippet.text.length <= 40 + 2)
    }

    @Test
    fun `short message is returned whole`() {
        val snippet = SearchSnippet.around("Find the Kane track", "kane", width = 80)
        assertEquals("Find the Kane track", snippet.text)
        assertEquals(9..12, snippet.match)
    }

    @Test
    fun `line breaks collapse into spaces`() {
        assertEquals("one two three", SearchSnippet.around("one\n\ntwo   three", "two", width = 80).text)
    }

    @Test
    fun `missing match keeps the beginning`() {
        val snippet = SearchSnippet.around(long, "absent", width = 20)
        assertNull(snippet.match)
        assertTrue(snippet.text.startsWith("Yesterday"))
    }
}
