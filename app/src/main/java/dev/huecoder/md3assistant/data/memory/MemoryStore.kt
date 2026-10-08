package dev.huecoder.md3assistant.data.memory

import android.util.Log
import dev.huecoder.md3assistant.data.db.MemoryDao
import dev.huecoder.md3assistant.data.db.MemoryEntity
import dev.huecoder.md3assistant.data.db.MemoryTier
import dev.huecoder.md3assistant.data.settings.MemorySettings
import dev.huecoder.md3assistant.llm.EmbeddingClient
import dev.huecoder.md3assistant.llm.EmbeddingPurpose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.random.Random

data class MemorySearchResult(val matches: List<RankedMemory<MemoryEntity>>, val semantic: Boolean, val warning: String?)

class MemoryStore(
    private val dao: MemoryDao,
    private val embeddings: EmbeddingClient,
    private val memorySettings: Flow<MemorySettings>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val all: Flow<List<MemoryEntity>> = dao.observeAll()

    suspend fun instant(): List<MemoryEntity> = dao.listTier(MemoryTier.INSTANT)

    suspend fun get(id: String): MemoryEntity? = dao.get(id.trim().lowercase())

    suspend fun save(tier: MemoryTier, title: String, content: String): MemoryEntity {
        val now = clock()
        val memory = validated(MemoryEntity(newId(), tier, title.trim(), content.trim(), now, now))
        if (tier == MemoryTier.INSTANT) checkInstantRoom()
        dao.upsert(memory)
        return memory
    }

    suspend fun update(id: String, title: String? = null, content: String? = null, tier: MemoryTier? = null): MemoryEntity? {
        val existing = get(id) ?: return null
        val changed = validated(
            existing.copy(
                title = title?.trim() ?: existing.title,
                content = content?.trim() ?: existing.content,
                tier = tier ?: existing.tier,
                updatedAt = clock(),
            ),
        )
        if (changed.tier == MemoryTier.INSTANT && existing.tier != MemoryTier.INSTANT) checkInstantRoom()
        val textChanged = changed.title != existing.title || changed.content != existing.content
        dao.upsert(if (textChanged) changed.copy(embedding = null, embeddingModel = null) else changed)
        return changed
    }

    suspend fun delete(id: String): Boolean = dao.delete(id.trim().lowercase()) > 0

    suspend fun search(query: String, limit: Int, tiers: Set<MemoryTier> = setOf(MemoryTier.LONG_TERM)): MemorySearchResult {
        val candidates = tiers.flatMap { dao.listTier(it) }
        if (candidates.isEmpty()) return MemorySearchResult(emptyList(), semantic = false, warning = null)
        val settings = memorySettings.first()
        val lexical = { memory: MemoryEntity -> MemoryRanking.lexical(query, memory.title, memory.content) }
        if (!settings.embeddingsReady) {
            return MemorySearchResult(MemoryRanking.rank(candidates, limit, lexical), semantic = false, warning = null)
        }
        return try {
            val embedded = withEmbeddings(candidates, settings)
            val queryVector = embeddings.embed(listOf(query), EmbeddingPurpose.QUERY, settings).single()
            val vectors = embedded.associate { it.id to it.embedding?.let(VectorCodec::decode) }
            val semantic = { memory: MemoryEntity -> vectors[memory.id]?.let { MemoryRanking.cosine(queryVector, it) } }
            MemorySearchResult(MemoryRanking.rank(embedded, limit, lexical, semantic), semantic = true, warning = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Semantic memory search failed, falling back to keywords", e)
            val warning = "Semantic search is unavailable (${e.message ?: e.javaClass.simpleName}), matched by keywords instead."
            MemorySearchResult(MemoryRanking.rank(candidates, limit, lexical), semantic = false, warning = warning)
        }
    }

    private suspend fun withEmbeddings(memories: List<MemoryEntity>, settings: MemorySettings): List<MemoryEntity> {
        val stale = memories.filter { it.embedding == null || it.embeddingModel != settings.embeddingSignature }
        if (stale.isEmpty()) return memories
        val vectors = embeddings.embed(stale.map(::embeddingText), EmbeddingPurpose.DOCUMENT, settings)
        val refreshed = stale.zip(vectors).associate { (memory, vector) ->
            memory.id to memory.copy(embedding = VectorCodec.encode(vector), embeddingModel = settings.embeddingSignature)
        }
        refreshed.values.forEach { dao.upsert(it) }
        return memories.map { refreshed[it.id] ?: it }
    }

    private suspend fun checkInstantRoom() {
        check(dao.listTier(MemoryTier.INSTANT).size < MAX_INSTANT) {
            "Instant memory is full ($MAX_INSTANT entries). Merge or delete entries, or save this one as long-term."
        }
    }

    private fun validated(memory: MemoryEntity): MemoryEntity {
        require(memory.title.isNotEmpty()) { "Memory title must not be empty" }
        require(memory.content.isNotEmpty()) { "Memory content must not be empty" }
        require(memory.title.length <= MAX_TITLE) { "Memory title is longer than $MAX_TITLE characters" }
        val maxContent = if (memory.tier == MemoryTier.INSTANT) MAX_INSTANT_CONTENT else MAX_CONTENT
        require(memory.content.length <= maxContent) { "Memory content is longer than $maxContent characters for this tier" }
        return memory
    }

    private suspend fun newId(): String {
        repeat(ID_ATTEMPTS) {
            val id = (1..ID_LENGTH).map { ID_ALPHABET[Random.nextInt(ID_ALPHABET.length)] }.joinToString("")
            if (dao.get(id) == null) return id
        }
        error("Could not allocate a memory id")
    }

    private fun embeddingText(memory: MemoryEntity) = "${memory.title}\n${memory.content}"

    companion object {
        const val MAX_INSTANT = 60
        const val MAX_TITLE = 120
        const val MAX_INSTANT_CONTENT = 600
        const val MAX_CONTENT = 4_000
        private const val TAG = "MemoryStore"
        private const val ID_LENGTH = 5
        private const val ID_ATTEMPTS = 20
        private const val ID_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
    }
}
