package dev.huecoder.md3agent.tools.web

import dev.huecoder.md3agent.data.settings.SearchBackend
import dev.huecoder.md3agent.data.settings.SearchSettings
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.fetchText
import dev.huecoder.md3agent.tools.ToolArgumentException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.URLDecoder

@Serializable
data class SearchHit(val title: String, val url: String, val snippet: String = "")

@Serializable
data class SearchResults(val query: String, val results: List<SearchHit>)

class WebSearcher(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, count: Int, settings: SearchSettings): List<SearchHit> {
        val hits = when (settings.backend) {
            SearchBackend.HTML_SCRAPE -> scrapeHtml(query)
            SearchBackend.SEARXNG -> searxng(query, settings)
            SearchBackend.BRAVE_API -> braveApi(query, settings)
        }
        return hits.distinctBy { it.url }.take(count)
    }

    private suspend fun scrapeHtml(query: String): List<SearchHit> {
        val request = Request.Builder()
            .url(HTML_SEARCH_URL)
            .header("User-Agent", BROWSER_USER_AGENT)
            .post(FormBody.Builder().add("q", query).build())
            .build()
        val html = httpClients.current().fetchText(request)
        val hits = DuckDuckGoHtml.parse(html)
        if (hits.isEmpty() && html.contains("anomaly", ignoreCase = true)) {
            throw IllegalStateException("The search engine asked for a captcha. Try again later or switch search to SearXNG in settings")
        }
        return hits
    }

    private suspend fun searxng(query: String, settings: SearchSettings): List<SearchHit> {
        val base = settings.searxngUrl.trim().toHttpUrlOrNull()
            ?: throw ToolArgumentException("SearXNG address is not set in tool settings")
        val url = base.newBuilder().addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .build()
        val body = httpClients.current().fetchText(Request.Builder().url(url).build())
        val results = json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { (it as? JsonObject)?.toHit(titleKey = "title", urlKey = "url", snippetKey = "content") }
    }

    private suspend fun braveApi(query: String, settings: SearchSettings): List<SearchHit> {
        if (settings.apiKey.isBlank()) throw ToolArgumentException("Search API key is not set in tool settings")
        val url = BRAVE_SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", MAX_API_RESULTS.toString())
            .build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", settings.apiKey.trim())
            .build()
        val body = httpClients.current().fetchText(request)
        val web = json.parseToJsonElement(body).jsonObject["web"] as? JsonObject ?: return emptyList()
        val results = web["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { (it as? JsonObject)?.toHit(titleKey = "title", urlKey = "url", snippetKey = "description") }
    }

    private fun JsonObject.toHit(titleKey: String, urlKey: String, snippetKey: String): SearchHit? {
        val url = this[urlKey]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") } ?: return null
        val title = this[titleKey]?.jsonPrimitive?.content?.let(HtmlText::inline).orEmpty().ifEmpty { url }
        val snippet = this[snippetKey]?.jsonPrimitive?.content?.let(HtmlText::inline).orEmpty()
        return SearchHit(title, url, snippet)
    }

    private companion object {
        const val HTML_SEARCH_URL = "https://html.duckduckgo.com/html/"
        const val BRAVE_SEARCH_URL = "https://api.search.brave.com/res/v1/web/search"
        const val MAX_API_RESULTS = 10
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
    }
}

object DuckDuckGoHtml {
    private val resultBlock = Regex("class=\"[^\"]*\\bresult__body\\b[^\"]*\"")
    private val titleLink = Regex("<a[^>]*class=\"[^\"]*\\bresult__a\\b[^\"]*\"[^>]*>([\\s\\S]*?)</a>")
    private val href = Regex("href=\"([^\"]+)\"")
    private val snippet = Regex("class=\"[^\"]*\\bresult__snippet\\b[^\"]*\"[^>]*>([\\s\\S]*?)</(a|div|td)>")

    fun parse(html: String): List<SearchHit> = html.split(resultBlock).drop(1).mapNotNull { block ->
        val link = titleLink.find(block) ?: return@mapNotNull null
        val target = href.find(link.value)?.groupValues?.get(1)?.let(HtmlText::decodeEntities)?.let(::unwrapRedirect)
            ?: return@mapNotNull null
        if (!target.startsWith("http") || target.contains("duckduckgo.com/y.js")) return@mapNotNull null
        SearchHit(
            title = HtmlText.inline(link.groupValues[1]).ifEmpty { target },
            url = target,
            snippet = snippet.find(block)?.groupValues?.get(1)?.let(HtmlText::inline).orEmpty(),
        )
    }

    private fun unwrapRedirect(link: String): String {
        val encoded = link.substringAfter("uddg=", missingDelimiterValue = "").substringBefore('&')
        return if (encoded.isEmpty()) link else URLDecoder.decode(encoded, Charsets.UTF_8.name())
    }
}
