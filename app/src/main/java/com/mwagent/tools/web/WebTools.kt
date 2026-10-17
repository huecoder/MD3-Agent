package com.mwagent.tools.web

import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class WebSearchTool(private val searcher: WebSearcher) : Tool {
    private val json = Json

    override val spec = ToolSpec(
        name = NAME,
        description = "Searches the web. Returns titles, links and snippets. " +
            "For details, open the relevant link with ${ReadWebpageTool.NAME}. Cite sources in your answer.",
        parameters = objectSchema(required = listOf("query")) {
            stringProperty("query", "Search query: just the core terms, no filler words")
            integerProperty("count", "How many results to return", minimum = 1, maximum = MAX_RESULTS)
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val query = arguments.requireString("query")
        val count = (arguments.optionalInt("count") ?: DEFAULT_RESULTS).coerceIn(1, MAX_RESULTS)
        val hits = searcher.search(query, count, settings.search)
        if (hits.isEmpty()) return ToolOutcome.error("Nothing found for \"$query\"")
        return ToolOutcome(json.encodeToString(SearchResults.serializer(), SearchResults(query, hits)))
    }

    companion object {
        const val NAME = "web_search"
        private const val DEFAULT_RESULTS = 5
        private const val MAX_RESULTS = 10
    }
}

class ReadWebpageTool(private val fetcher: PageFetcher) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens a web page by URL and returns its readable text, usually as markdown. " +
            "Long pages come in parts: when the result says it is truncated, call again with the given offset to read on.",
        parameters = objectSchema(required = listOf("url")) {
            stringProperty("url", "Full http(s) address of the page")
            integerProperty("offset", "Character offset to continue reading from, default 0", minimum = 0, maximum = Int.MAX_VALUE)
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val raw = arguments.requireString("url")
        val url = raw.toHttpUrlOrNull() ?: throw ToolArgumentException("Not an http(s) address: \"$raw\"")
        val offset = (arguments.optionalInt("offset") ?: 0).coerceAtLeast(0)
        val page = fetcher.fetch(url, settings.fetch)
        if (page.text.isBlank()) return ToolOutcome.error("The page has no text (it may be rendered by scripts)")
        if (offset >= page.text.length) return ToolOutcome.error("Offset $offset is past the end of the page (${page.text.length} characters)")
        val end = minOf(offset + MAX_TEXT_CHARS, page.text.length)
        val tail = if (end < page.text.length) "\n\n…[truncated at ${end} of ${page.text.length} characters, continue with offset=$end]" else ""
        val header = listOfNotNull(page.title?.let { "# $it" }, url.toString())
        return ToolOutcome((header + (page.text.substring(offset, end) + tail)).joinToString("\n\n"))
    }

    companion object {
        const val NAME = "read_webpage"
        private const val MAX_TEXT_CHARS = 16_000
    }
}
