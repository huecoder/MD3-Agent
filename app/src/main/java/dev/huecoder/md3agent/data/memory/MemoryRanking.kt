package dev.huecoder.md3agent.data.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Locale

data class RankedMemory<T>(val item: T, val score: Float)

object MemoryRanking {
    private const val STEM_LENGTH = 5
    private const val MIN_TOKEN_LENGTH = 2
    private const val TITLE_WEIGHT = 1.5f
    private const val SEMANTIC_WEIGHT = 0.7f
    private const val MIN_SEMANTIC_SCORE = 0.45f
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    fun tokens(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(SEPARATORS).filter { it.length >= MIN_TOKEN_LENGTH }

    fun lexical(query: String, title: String, content: String): Float {
        val wanted = tokens(query).distinct()
        if (wanted.isEmpty()) return 0f
        val titleTokens = tokens(title)
        val contentTokens = tokens(content)
        val hits = wanted.sumOf { token ->
            when {
                titleTokens.any { matches(token, it) } -> TITLE_WEIGHT.toDouble()
                contentTokens.any { matches(token, it) } -> 1.0
                else -> 0.0
            }
        }
        return (hits / (wanted.size * TITLE_WEIGHT)).toFloat()
    }

    fun cosine(first: FloatArray, second: FloatArray): Float {
        if (first.size != second.size) return 0f
        var dot = 0f
        for (index in first.indices) dot += first[index] * second[index]
        return dot
    }

    fun <T> rank(
        items: List<T>,
        limit: Int,
        lexicalScore: (T) -> Float,
        semanticScore: ((T) -> Float?)? = null,
    ): List<RankedMemory<T>> = items
        .map { item ->
            val lexical = lexicalScore(item)
            val semantic = semanticScore?.invoke(item)
            val score = when {
                semantic == null -> lexical
                semantic < MIN_SEMANTIC_SCORE && lexical == 0f -> 0f
                else -> SEMANTIC_WEIGHT * semantic + (1f - SEMANTIC_WEIGHT) * lexical
            }
            RankedMemory(item, score)
        }
        .filter { it.score > 0f }
        .sortedByDescending { it.score }
        .take(limit)

    private fun matches(wanted: String, candidate: String): Boolean {
        if (wanted == candidate) return true
        val stem = wanted.take(minOf(STEM_LENGTH, wanted.length - 1))
        return stem.length >= STEM_LENGTH - 1 && candidate.startsWith(stem)
    }
}

object VectorCodec {
    fun encode(vector: FloatArray): String {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach(buffer::putFloat)
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    fun decode(encoded: String): FloatArray? = runCatching {
        val buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).order(ByteOrder.LITTLE_ENDIAN)
        FloatArray(buffer.remaining() / Float.SIZE_BYTES) { buffer.getFloat() }
    }.getOrNull()
}
