package dev.huecoder.md3assistant.llm

import dev.huecoder.md3assistant.data.settings.ProviderProfile
import dev.huecoder.md3assistant.net.HttpClientProvider
import dev.huecoder.md3assistant.net.fetchText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class TranscriptionClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun transcribe(provider: ProviderProfile, model: String, language: String, audio: File, mimeType: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .apply { if (language.isNotBlank()) addFormDataPart("language", language.trim()) }
            .addFormDataPart("file", audio.name, audio.asRequestBody(mimeType.toMediaType()))
            .build()
        val request = ProviderEndpoint(provider).authorizedRequest(TRANSCRIPTIONS_PATH).post(body).build()
        return textOf(httpClients.current().fetchText(request))
    }

    private fun textOf(raw: String): String {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw ProviderException.MalformedResponse("Сервер распознавания вернул не JSON: ${raw.take(ERROR_PREVIEW_CHARS)}")
        return (parsed["text"] as? JsonPrimitive)?.contentOrNull?.trim()
            ?: throw ProviderException.MalformedResponse("В ответе распознавания нет поля text")
    }

    private companion object {
        const val TRANSCRIPTIONS_PATH = "audio/transcriptions"
        const val ERROR_PREVIEW_CHARS = 200
    }
}
