package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.executeCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import java.io.IOException
import java.io.InputStream

data class SpeechVoice(
    val id: String,
    val name: String,
    val description: String? = null,
    val traits: List<String> = emptyList(),
    val previewUrl: String? = null,
)

@Serializable
private class VoiceListing(val voices: List<VoiceEntry>? = null)

@Serializable
private class VoiceEntry(
    @SerialName("voice_id") val id: String? = null,
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val labels: JsonObject? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
)

class VoiceListClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(provider: ProviderProfile): List<SpeechVoice> = when (provider.format) {
        ApiFormat.ELEVENLABS -> elevenLabs(provider)
        ApiFormat.GEMINI -> gemini(provider)
        ApiFormat.OPENAI -> BuiltInVoices.OPENAI
        ApiFormat.ANTHROPIC -> emptyList()
    }

    private suspend fun elevenLabs(provider: ProviderProfile): List<SpeechVoice> {
        val request = ProviderEndpoint(provider).authorizedRequest(ELEVENLABS_VOICES_PATH).get().build()
        return withNetworkErrors {
            httpClients.current().newCall(request).executeCancellable { response ->
                if (!response.isSuccessful) throw httpFailure(response.code, response.peekBody(ERROR_PREVIEW_BYTES).string())
                readElevenLabs(response.body.byteStream())
            }
        }
    }

    private suspend fun gemini(provider: ProviderProfile): List<SpeechVoice> {
        val endpoint = ProviderEndpoint(provider)
        val voices = mutableListOf<SpeechVoice>()
        var pageToken: String? = null
        var pages = 0
        do {
            val url = endpoint.url(GEMINI_VOICES_PATH).newBuilder().geminiQuery(pageToken).build()
            val request = endpoint.authorizedRequest(url).get().build()
            val page = withNetworkErrors {
                httpClients.current().newCall(request).executeCancellable { response ->
                    if (!response.isSuccessful) throw httpFailure(response.code, response.peekBody(ERROR_PREVIEW_BYTES).string())
                    readGemini(response.body.string())
                }
            }
            voices += page.first
            pageToken = page.second
        } while (!pageToken.isNullOrBlank() && ++pages < MAX_PAGES)
        return voices.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    private fun HttpUrl.Builder.geminiQuery(pageToken: String?): HttpUrl.Builder {
        addQueryParameter("type", GEMINI_PREBUILT)
        addQueryParameter("page_size", GEMINI_PAGE_SIZE)
        if (!pageToken.isNullOrBlank()) addQueryParameter("page_token", pageToken)
        return this
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun readElevenLabs(body: InputStream): List<SpeechVoice> {
        val entries = try {
            json.decodeFromStream<VoiceListing>(body).voices
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw ProviderException.MalformedResponse(UNKNOWN_FORMAT)
        return entries.mapNotNull(::elevenLabsVoice)
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }

    private fun elevenLabsVoice(entry: VoiceEntry): SpeechVoice? {
        val id = entry.id?.takeIf { it.isNotBlank() } ?: return null
        val labels = entry.labels
        val traits = buildList {
            entry.category?.let { add(humanized(it)) }
            ELEVENLABS_LABELS.forEach { key -> labels?.text(key)?.let { add(if (key == LANGUAGE_LABEL) languageCode(it) else humanized(it)) } }
            labels?.keys.orEmpty().filter { it !in ELEVENLABS_LABELS }.forEach { key -> labels?.text(key)?.let { add(humanized(it)) } }
        }
        return SpeechVoice(
            id = id,
            name = entry.name?.takeIf { it.isNotBlank() } ?: id,
            description = entry.description?.takeIf { it.isNotBlank() },
            traits = traits.distinct(),
            previewUrl = entry.previewUrl?.takeIf { it.isNotBlank() },
        )
    }

    private fun readGemini(body: String): Pair<List<SpeechVoice>, String?> {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: throw ProviderException.MalformedResponse(UNKNOWN_FORMAT)
        val entries = root["voices"] as? JsonArray ?: if (root.isEmpty()) JsonArray(emptyList()) else throw ProviderException.MalformedResponse(UNKNOWN_FORMAT)
        val voices = entries.mapNotNull { (it as? JsonObject)?.let(::geminiVoice) }
        return voices to root.text(NEXT_PAGE_TOKEN, NEXT_PAGE_TOKEN_CAMEL)
    }

    private fun geminiVoice(entry: JsonObject): SpeechVoice? {
        val id = entry.text("id") ?: return null
        val traits = listOfNotNull(
            entry.text("gender")?.let(::humanized),
            entry.text("pitch")?.let { humanized("$it pitch") },
            entry.text("accent")?.let(::humanized),
            entry.text("language_code", "languageCode"),
            entry.text("persona"),
            entry.text("context"),
        )
        return SpeechVoice(
            id = id,
            name = entry.text("display_name", "displayName") ?: id,
            description = entry.text("description"),
            traits = traits.distinct(),
        )
    }

    private fun JsonObject.text(vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } }

    private fun humanized(value: String): String = value.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

    private fun languageCode(value: String): String = if (value.length <= SHORT_LANGUAGE_CODE) value.uppercase() else humanized(value)

    private suspend fun <T> withNetworkErrors(block: suspend () -> T): T = try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: IOException) {
        throw ProviderException.Network(e)
    }

    private fun httpFailure(code: Int, excerpt: String) = ProviderException.Http(code, "HTTP $code: $excerpt")

    private companion object {
        const val ELEVENLABS_VOICES_PATH = "voices"
        const val GEMINI_VOICES_PATH = "voices"
        const val GEMINI_PREBUILT = "prebuilt"
        const val GEMINI_PAGE_SIZE = "1000"
        const val NEXT_PAGE_TOKEN = "next_page_token"
        const val NEXT_PAGE_TOKEN_CAMEL = "nextPageToken"
        const val MAX_PAGES = 5
        const val SHORT_LANGUAGE_CODE = 3
        const val LANGUAGE_LABEL = "language"
        const val ERROR_PREVIEW_BYTES = 300L
        const val UNKNOWN_FORMAT = "Server returned the voice list in an unknown format"
        val ELEVENLABS_LABELS = listOf("gender", "age", "accent", "descriptive", "use_case", "language")
    }
}

object BuiltInVoices {
    val OPENAI = listOf("alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer", "verse", "marin", "cedar")
        .map { SpeechVoice(id = it, name = it.replaceFirstChar { letter -> letter.uppercase() }) }
}
