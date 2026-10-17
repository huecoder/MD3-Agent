package com.mwagent.llm

import com.mwagent.data.settings.ApiFormat
import com.mwagent.data.settings.ProviderProfile
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class ProviderEndpoint(private val provider: ProviderProfile) {
    private val base: HttpUrl = normalizeBase(provider.baseUrl)
        ?: throw ProviderException.InvalidConfiguration("Invalid provider address: ${provider.baseUrl}")

    fun url(path: String): HttpUrl = base.newBuilder().addPathSegments(path).build()

    fun authorizedRequest(path: String): Request.Builder = authorizedRequest(url(path))

    fun authorizedRequest(url: HttpUrl): Request.Builder {
        val builder = Request.Builder().url(url)
        val key = provider.apiKey.trim()
        when (provider.format) {
            ApiFormat.OPENAI -> if (key.isNotEmpty()) builder.header("Authorization", "Bearer $key")
            ApiFormat.ANTHROPIC -> {
                if (key.isNotEmpty()) builder.header("x-api-key", key)
                builder.header("anthropic-version", ANTHROPIC_VERSION)
            }
            ApiFormat.GEMINI -> if (key.isNotEmpty()) builder.header("x-goog-api-key", key)
            ApiFormat.ELEVENLABS -> if (key.isNotEmpty()) builder.header("xi-api-key", key)
        }
        provider.extraHeaders.filter { it.name.isNotBlank() }.forEach { builder.header(it.name.trim(), it.value) }
        return builder
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        val KNOWN_ENDPOINT_SUFFIXES = listOf("/chat/completions", "/messages", "/models", "/")

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
