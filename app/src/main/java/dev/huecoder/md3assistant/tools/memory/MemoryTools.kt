package dev.huecoder.md3assistant.tools.memory

import dev.huecoder.md3assistant.data.db.MemoryEntity
import dev.huecoder.md3assistant.data.db.MemoryTier
import dev.huecoder.md3assistant.data.memory.MemoryStore
import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolSpec
import dev.huecoder.md3assistant.tools.Tool
import dev.huecoder.md3assistant.tools.ToolArgumentException
import dev.huecoder.md3assistant.tools.ToolOutcome
import dev.huecoder.md3assistant.tools.integerProperty
import dev.huecoder.md3assistant.tools.objectSchema
import dev.huecoder.md3assistant.tools.optionalInt
import dev.huecoder.md3assistant.tools.optionalString
import dev.huecoder.md3assistant.tools.requireString
import dev.huecoder.md3assistant.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val INSTANT = "instant"
private const val LONG_TERM = "long_term"

private fun JsonObjectBuilder.tierProperty(description: String) = putJsonObject("tier") {
    put("type", "string")
    put("description", description)
    putJsonArray("enum") {
        add(JsonPrimitive(INSTANT))
        add(JsonPrimitive(LONG_TERM))
    }
}

private fun parseTier(raw: String?): MemoryTier? = when (raw?.lowercase()) {
    null -> null
    INSTANT -> MemoryTier.INSTANT
    LONG_TERM, "long-term", "longterm" -> MemoryTier.LONG_TERM
    else -> throw ToolArgumentException("tier must be \"$INSTANT\" or \"$LONG_TERM\"")
}

private fun MemoryEntity.describe(): String {
    val tierName = if (tier == MemoryTier.INSTANT) INSTANT else LONG_TERM
    return "[$id] ($tierName) $title: $content"
}

private suspend fun guarded(block: suspend () -> ToolOutcome): ToolOutcome = try {
    block()
} catch (e: ToolArgumentException) {
    throw e
} catch (e: IllegalArgumentException) {
    ToolOutcome.error(e.message.orEmpty())
}

class MemorySaveTool(private val store: MemoryStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Saves a fact to memory so you remember it in future chats. Use \"$INSTANT\" for important preferences and " +
            "personal facts about the user that should always be in front of you (name, language, style, family, job). Use " +
            "\"$LONG_TERM\" for everything else worth recalling later; long-term memories are found with ${MemorySearchTool.NAME}. " +
            "Check existing memories first and update instead of saving duplicates.",
        parameters = objectSchema(required = listOf("tier", "title", "content")) {
            tierProperty("Where to keep the memory")
            stringProperty("title", "Short description of what this memory is about, up to ${MemoryStore.MAX_TITLE} characters")
            stringProperty("content", "The fact itself, self-contained and written in third person about the user")
        },
    )

    override fun promptNote(settings: ToolSettings) =
        "You have a two-layer memory. Instant memories are listed in this prompt; search long-term memory with " +
            "${MemorySearchTool.NAME} when past context could help. Save new lasting facts about the user on your own."

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome = guarded {
        val tier = parseTier(arguments.requireString("tier")) ?: MemoryTier.LONG_TERM
        val memory = store.save(tier, arguments.requireString("title"), arguments.requireString("content"))
        ToolOutcome("Saved ${memory.describe()}")
    }

    companion object {
        const val NAME = "memory_save"
    }
}

class MemoryUpdateTool(private val store: MemoryStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Edits a saved memory by its id: rewrite the title or content, or move it between tiers.",
        parameters = objectSchema(required = listOf("id")) {
            stringProperty("id", "Memory id, shown in brackets")
            stringProperty("title", "New title")
            stringProperty("content", "New content")
            tierProperty("New tier")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome = guarded {
        val id = arguments.requireString("id")
        val title = arguments.optionalString("title")
        val content = arguments.optionalString("content")
        val tier = parseTier(arguments.optionalString("tier"))
        if (title == null && content == null && tier == null) throw ToolArgumentException("Nothing to change: give title, content or tier")
        val memory = store.update(id, title, content, tier) ?: return@guarded ToolOutcome.error("No memory with id \"$id\"")
        ToolOutcome("Updated ${memory.describe()}")
    }

    companion object {
        const val NAME = "memory_update"
    }
}

class MemoryDeleteTool(private val store: MemoryStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Deletes a saved memory by its id, for facts that are wrong or no longer true.",
        parameters = objectSchema(required = listOf("id")) {
            stringProperty("id", "Memory id, shown in brackets")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val id = arguments.requireString("id")
        return if (store.delete(id)) ToolOutcome("Deleted memory $id") else ToolOutcome.error("No memory with id \"$id\"")
    }

    companion object {
        const val NAME = "memory_delete"
    }
}

class MemorySearchTool(private val store: MemoryStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Searches long-term memory by meaning and keywords. Returns the best matching memories with their ids.",
        parameters = objectSchema(required = listOf("query")) {
            stringProperty("query", "What to look for, a few words or a question")
            integerProperty("limit", "How many memories to return", minimum = 1, maximum = MAX_LIMIT)
            scopeProperty()
        },
    )

    override val timeoutMs: Long = SEARCH_TIMEOUT_MS

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val query = arguments.requireString("query")
        val limit = (arguments.optionalInt("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val tiers = if (arguments.optionalString("scope") == "all") MemoryTier.entries.toSet() else setOf(MemoryTier.LONG_TERM)
        val result = store.search(query, limit, tiers)
        val lines = buildList {
            result.warning?.let(::add)
            if (result.matches.isEmpty()) add("No matching memories.")
            result.matches.forEach { add(it.item.describe()) }
        }
        return ToolOutcome(lines.joinToString("\n"))
    }

    private fun JsonObjectBuilder.scopeProperty() = putJsonObject("scope") {
        put("type", "string")
        put("description", "\"long_term\" by default, \"all\" to include instant memories too")
        putJsonArray("enum") {
            add(JsonPrimitive(LONG_TERM))
            add(JsonPrimitive("all"))
        }
    }

    companion object {
        const val NAME = "memory_search"
        private const val DEFAULT_LIMIT = 8
        private const val MAX_LIMIT = 25
        private const val SEARCH_TIMEOUT_MS = 90_000L
    }
}
