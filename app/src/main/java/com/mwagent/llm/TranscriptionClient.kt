package com.mwagent.llm

import com.mwagent.data.settings.ApiFormat
import com.mwagent.data.settings.ProviderProfile
import com.mwagent.llm.wire.GeminiGenerate
import com.mwagent.net.HttpClientProvider
import com.mwagent.net.fetchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Base64

class TranscriptionClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun transcribe(provider: ProviderProfile, model: String, language: String, audio: File, mimeType: String): String =
        when (provider.format) {
            ApiFormat.OPENAI -> openAi(provider, model, language, audio, mimeType)
            ApiFormat.GEMINI -> gemini(provider, model, language, audio, mimeType)
            ApiFormat.ELEVENLABS -> elevenLabs(provider, model, language, audio, mimeType)
            ApiFormat.ANTHROPIC -> throw ProviderException.InvalidConfiguration("Anthropic has no speech recognition API")
        }

    private suspend fun openAi(provider: ProviderProfile, model: String, language: String, audio: File, mimeType: String): String {
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

    private suspend fun elevenLabs(provider: ProviderProfile, model: String, language: String, audio: File, mimeType: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model_id", model)
            .addFormDataPart("tag_audio_events", "false")
            .addFormDataPart("timestamps_granularity", "none")
            .apply { if (language.isNotBlank()) addFormDataPart("language_code", language.trim()) }
            .addFormDataPart("file", audio.name, audio.asRequestBody(mimeType.toMediaType()))
            .build()
        val request = ProviderEndpoint(provider).authorizedRequest(ELEVENLABS_TRANSCRIPTIONS_PATH).post(body).build()
        return textOf(httpClients.current().fetchText(request))
    }

    private suspend fun gemini(provider: ProviderProfile, model: String, language: String, audio: File, mimeType: String): String {
        val encoded = withContext(Dispatchers.IO) { Base64.getEncoder().encodeToString(audio.readBytes()) }
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", transcriptionPrompt(language)) }
                        addJsonObject { putJsonObject("inlineData") { put("mimeType", mimeType); put("data", encoded) } }
                    }
                }
            }
        }
        val request = GeminiGenerate.request(ProviderEndpoint(provider), model, body)
        return GeminiGenerate.text(GeminiGenerate.parts(httpClients.current().fetchText(request)))
    }

    private fun transcriptionPrompt(language: String): String {
        val hint = language.trim().takeIf { it.isNotEmpty() }?.let { " The speech is in \"$it\"." }.orEmpty()
        return GEMINI_PROMPT + hint
    }

    private fun textOf(raw: String): String {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw ProviderException.MalformedResponse("Transcription server returned non-JSON: ${raw.take(ERROR_PREVIEW_CHARS)}")
        return (parsed["text"] as? JsonPrimitive)?.contentOrNull?.trim()
            ?: throw ProviderException.MalformedResponse("Transcription response has no text field")
    }

    private companion object {
        const val TRANSCRIPTIONS_PATH = "audio/transcriptions"
        const val ELEVENLABS_TRANSCRIPTIONS_PATH = "speech-to-text"
        const val ERROR_PREVIEW_CHARS = 200
        const val GEMINI_PROMPT = "Transcribe the speech in this audio word for word. Reply with the transcript only, without quotes or commentary. " +
            "If there is no speech, reply with an empty message."
    }
}
