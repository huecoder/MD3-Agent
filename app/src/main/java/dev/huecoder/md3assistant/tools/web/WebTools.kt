package dev.huecoder.md3assistant.tools.web

import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolSpec
import dev.huecoder.md3assistant.net.HttpClientProvider
import dev.huecoder.md3assistant.net.fetchText
import dev.huecoder.md3assistant.tools.Tool
import dev.huecoder.md3assistant.tools.ToolArgumentException
import dev.huecoder.md3assistant.tools.ToolOutcome
import dev.huecoder.md3assistant.tools.integerProperty
import dev.huecoder.md3assistant.tools.objectSchema
import dev.huecoder.md3assistant.tools.optionalInt
import dev.huecoder.md3assistant.tools.requireString
import dev.huecoder.md3assistant.tools.stringProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

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

class ReadWebpageTool(private val httpClients: HttpClientProvider) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens a web page by URL and returns its text.",
        parameters = objectSchema(required = listOf("url")) {
            stringProperty("url", "Full http(s) address of the page")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val raw = arguments.requireString("url")
        val url = raw.toHttpUrlOrNull() ?: throw ToolArgumentException("Not an http(s) address: \"$raw\"")
        val request = Request.Builder().url(url).header("Accept", "text/html,text/plain;q=0.9,*/*;q=0.5").build()
        val html = httpClients.current().fetchText(request)
        val text = HtmlText.document(html)
        if (text.isBlank()) return ToolOutcome.error("The page has no text (it may be rendered by scripts)")
        val title = HtmlText.title(html)
        val clipped = if (text.length > MAX_TEXT_CHARS) text.take(MAX_TEXT_CHARS) + "\n…[truncated]" else text
        return ToolOutcome(listOfNotNull(title?.let { "# $it" }, url.toString(), clipped).joinToString("\n\n"))
    }

    companion object {
        const val NAME = "read_webpage"
        private const val MAX_TEXT_CHARS = 12_000
    }
}
