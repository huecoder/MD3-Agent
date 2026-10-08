package dev.huecoder.md3agent.data.memory

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRankingTest {
    @Test
    fun `russian word forms match through stems`() {
        val score = MemoryRanking.lexical("кошка", "Питомец", "У пользователя есть кошки по имени Моти")
        assertTrue(score > 0f)
    }

    @Test
    fun `title hits outweigh content hits`() {
        val inTitle = MemoryRanking.lexical("coffee", "Coffee order", "oat flat white")
        val inContent = MemoryRanking.lexical("coffee", "Morning", "drinks coffee daily")
        assertEquals(1f, inTitle, 0.0001f)
        assertTrue(inContent in 0f..inTitle && inContent > 0f)
    }

    @Test
    fun `short and empty queries score zero`() {
        assertEquals(0f, MemoryRanking.lexical("a", "a title", "a body"), 0f)
        assertEquals(0f, MemoryRanking.lexical("  ", "title", "body"), 0f)
    }

    @Test
    fun `keyword rank drops misses and respects limit`() {
        val items = listOf("cats and dogs", "dogs only", "nothing here", "dogs dogs")
        val ranked = MemoryRanking.rank(items, limit = 2, lexicalScore = { MemoryRanking.lexical("dogs", "", it) })
        assertEquals(2, ranked.size)
        assertTrue(ranked.none { it.item == "nothing here" })
    }

    @Test
    fun `weak semantic match without keywords is dropped`() {
        val ranked = MemoryRanking.rank(
            items = listOf("weak", "strong", "keyword"),
            limit = 10,
            lexicalScore = { if (it == "keyword") 0.5f else 0f },
            semanticScore = { mapOf("weak" to 0.3f, "strong" to 0.8f, "keyword" to 0.2f)[it] },
        )
        assertEquals(listOf("strong", "keyword"), ranked.map { it.item })
    }

    @Test
    fun `missing vector falls back to keyword score`() {
        val ranked = MemoryRanking.rank(
            items = listOf("unembedded"),
            limit = 1,
            lexicalScore = { 0.4f },
            semanticScore = { null },
        )
        assertEquals(0.4f, ranked.single().score, 0.0001f)
    }

    @Test
    fun `cosine of mismatched sizes is zero`() {
        assertEquals(0f, MemoryRanking.cosine(floatArrayOf(1f), floatArrayOf(1f, 0f)), 0f)
        assertEquals(1f, MemoryRanking.cosine(floatArrayOf(0.6f, 0.8f), floatArrayOf(0.6f, 0.8f)), 0.0001f)
    }

    @Test
    fun `vector codec round trips and rejects garbage`() {
        val vector = floatArrayOf(0.1f, -2.5f, 3.25f, 0f)
        assertArrayEquals(vector, VectorCodec.decode(VectorCodec.encode(vector)), 0f)
        assertNull(VectorCodec.decode("not base64 !!"))
    }
}
