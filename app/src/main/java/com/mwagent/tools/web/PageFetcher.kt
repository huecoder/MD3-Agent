package com.mwagent.tools.web

import com.mwagent.data.settings.FetchBackend
import com.mwagent.data.settings.FetchSettings
import com.mwagent.net.HttpClientProvider
import com.mwagent.net.fetchText
import com.mwagent.tools.ToolArgumentException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

data class FetchedPage(val title: String?, val text: String)

class PageFetcher(private val httpClients: HttpClientProvider) {
    suspend fun fetch(url: HttpUrl, settings: FetchSettings): FetchedPage = when (settings.backend) {
        FetchBackend.DIRECT -> direct(url)
        FetchBackend.JINA_READER -> throughReader(FetchSettings.JINA_READER_URL, settings.jinaApiKey, url)
        FetchBackend.CUSTOM -> throughReader(settings.customUrl, settings.customApiKey, url)
    }

    private suspend fun direct(url: HttpUrl): FetchedPage {
        val request = Request.Builder().url(url).header("Accept", "text/html,text/plain;q=0.9,*/*;q=0.5").build()
        return pageOf(httpClients.current().fetchText(request))
    }

    private suspend fun throughReader(template: String, apiKey: String, url: HttpUrl): FetchedPage {
        val readerUrl = readerAddress(template, url)
        val request = Request.Builder().url(readerUrl)
            .apply { apiKey.trim().takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        return pageOf(httpClients.current().fetchText(request))
    }

    private fun readerAddress(template: String, url: HttpUrl): HttpUrl {
        val trimmed = template.trim()
        if (trimmed.isEmpty()) throw ToolArgumentException("Page reader address is not set in tool settings")
        val filled = if (FetchSettings.URL_PLACEHOLDER in trimmed) {
            trimmed.replace(FetchSettings.URL_PLACEHOLDER, url.toString())
        } else {
            trimmed + url.toString()
        }
        return filled.toHttpUrlOrNull() ?: throw ToolArgumentException("Page reader address is not a valid URL: \"$trimmed\"")
    }

    private fun pageOf(body: String): FetchedPage =
        if (looksLikeHtml(body)) FetchedPage(HtmlText.title(body), HtmlText.document(body)) else FetchedPage(null, body.trim())

    private fun looksLikeHtml(body: String): Boolean {
        val head = body.trimStart().take(HTML_SNIFF_CHARS).lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html") || "<body" in head
    }

    private companion object {
        const val HTML_SNIFF_CHARS = 1_024
    }
}
