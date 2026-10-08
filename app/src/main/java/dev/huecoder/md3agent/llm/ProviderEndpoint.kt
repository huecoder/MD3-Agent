package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ProviderProfile
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class ProviderEndpoint(private val provider: ProviderProfile) {
    private val base: HttpUrl = normalizeBase(provider.baseUrl)
        ?: throw ProviderException.InvalidConfiguration("Invalid provider address: ${provider.baseUrl}")

    fun url(path: String): HttpUrl = base.newBuilder().addPathSegments(path).build()

    fun authorizedRequest(path: String): Request.Builder {
        val builder = Request.Builder().url(url(path))
        if (provider.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${provider.apiKey}")
        provider.extraHeaders.filter { it.name.isNotBlank() }.forEach { builder.header(it.name.trim(), it.value) }
        return builder
    }

    private companion object {
        val KNOWN_ENDPOINT_SUFFIXES = listOf("/chat/completions", "/models", "/")

        fun normalizeBase(raw: String): HttpUrl? {
            var trimmed = raw.trim()
            KNOWN_ENDPOINT_SUFFIXES.forEach { suffix -> trimmed = trimmed.removeSuffix(suffix) }
            return trimmed.toHttpUrlOrNull()
        }
    }
}

sealed class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidConfiguration(message: String) : ProviderException(message)
    class Http(val code: Int, message: String) : ProviderException(message)
    class Network(cause: Throwable) : ProviderException(cause.message ?: "Network error", cause)
    class MalformedResponse(message: String) : ProviderException(message)
}
