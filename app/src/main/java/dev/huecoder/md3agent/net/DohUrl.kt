package dev.huecoder.md3agent.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object DohUrl {
    private const val DEFAULT_PATH = "/dns-query"

    fun parse(input: String): HttpUrl? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (!url.isHttps) return null
        return if (url.encodedPath == "/") url.newBuilder().encodedPath(DEFAULT_PATH).build() else url
    }
}
