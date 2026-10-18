package dev.huecoder.md3agent.tools.web

import dev.huecoder.md3agent.data.settings.CustomSearch
import dev.huecoder.md3agent.data.settings.SearchMethod
import dev.huecoder.md3agent.tools.ToolArgumentException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

internal object CustomSearchApi {
    private val placeholder = Regex("""\{(query|count|key)}""")
    private val jsonBody = "application/json; charset=utf-8".toMediaType()

    fun request(custom: CustomSearch, query: String, count: Int): Request {
        val template = custom.url.trim()
        if (template.isEmpty()) throw ToolArgumentException("Custom search address is not set in tool settings")
        if (custom.needsKey && custom.apiKey.isBlank()) throw ToolArgumentException("Custom search needs an API key, it is not set in tool settings")
        val values = mapOf("query" to query, "count" to count.toString(), "key" to custom.apiKey.trim())
        val url = fill(template, values, ::urlEncoded).toHttpUrlOrNull()
            ?: throw ToolArgumentException("Custom search address is not a valid URL: \"$template\"")
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        custom.headers.filter { it.name.isNotBlank() }.forEach { builder.header(it.name.trim(), fill(it.value, values) { text -> text }) }
        if (custom.method == SearchMethod.GET) return builder.get().build()
        if (custom.body.isBlank()) throw ToolArgumentException("Custom search sends a POST request, so its body is needed in tool settings")
        return builder.post(fill(custom.body, values, ::jsonEscaped).toRequestBody(jsonBody)).build()
    }

    fun hits(response: JsonElement, custom: CustomSearch): List<SearchHit> {
        val found = valueAt(response, custom.resultsPath)
        val items = found as? JsonArray ?: throw ToolArgumentException(missingList(response, custom.resultsPath))
        return items.mapNotNull { (it as? JsonObject)?.let { item -> hit(item, custom) } }
    }

    private fun hit(item: JsonObject, custom: CustomSearch): SearchHit? {
        val link = textAt(item, custom.linkField).takeIf { it.startsWith("http") } ?: return null
        val title = HtmlText.inline(textAt(item, custom.titleField)).ifEmpty { link }
        val snippet = custom.snippetFields.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .map { textAt(item, it) }.firstOrNull { it.isNotBlank() }
            ?.let(HtmlText::inline)?.take(MAX_SNIPPET_CHARS).orEmpty()
        return SearchHit(title, link, snippet)
    }

    private fun fill(template: String, values: Map<String, String>, encode: (String) -> String): String =
        placeholder.replace(template) { match -> encode(values.getValue(match.groupValues[1])) }

    private fun urlEncoded(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name())

    private fun jsonEscaped(text: String): String = JsonPrimitive(text).toString().removeSurrounding("\"")

    private fun valueAt(root: JsonElement, path: String): JsonElement? =
        path.split('.').map { it.trim() }.filter { it.isNotEmpty() }.fold<String, JsonElement?>(root) { current, step ->
            when (current) {
                is JsonObject -> current[step]
                is JsonArray -> step.toIntOrNull()?.let { index -> current.getOrNull(index) }
                else -> null
            }
        }

    private fun textAt(item: JsonObject, path: String): String = when (val value = valueAt(item, path)) {
        null, JsonNull -> ""
        is JsonPrimitive -> value.content
        is JsonArray -> value.joinToString(SNIPPET_JOINER) { (it as? JsonPrimitive)?.content.orEmpty() }
        is JsonObject -> ""
    }

    private fun missingList(response: JsonElement, path: String): String {
        val where = if (path.isBlank()) "at the top of the response" else "at \"$path\""
        val keys = (response as? JsonObject)?.keys?.joinToString(", ")?.takeIf { it.isNotEmpty() }
        return "The search response has no list of results $where" + (keys?.let { ", it has: $it" } ?: "") + ". Check the results path in tool settings"
    }

    private const val MAX_SNIPPET_CHARS = 600
    private const val SNIPPET_JOINER = " … "
}
