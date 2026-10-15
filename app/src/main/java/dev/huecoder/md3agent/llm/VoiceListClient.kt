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
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import java.io.InputStream

data class SpeechVoice(val id: String, val name: String)

@Serializable
private class VoiceListing(val voices: List<VoiceEntry>? = null)

@Serializable
private class VoiceEntry(@SerialName("voice_id") val id: String? = null, val name: String? = null)

class VoiceListClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(provider: ProviderProfile): List<SpeechVoice> {
        if (provider.format != ApiFormat.ELEVENLABS) return emptyList()
        val request = ProviderEndpoint(provider).authorizedRequest(VOICES_PATH).get().build()
        return try {
            withContext(Dispatchers.IO) {
                httpClients.current().newCall(request).executeCancellable { response ->
                    if (!response.isSuccessful) {
                        val excerpt = response.peekBody(ERROR_PREVIEW_BYTES).string()
                        throw ProviderException.Http(response.code, "HTTP ${response.code}: $excerpt")
                    }
                    read(response.body.byteStream())
                }
            }
        } catch (e: IOException) {
            throw ProviderException.Network(e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun read(body: InputStream): List<SpeechVoice> {
        val entries = try {
            json.decodeFromStream<VoiceListing>(body).voices
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw ProviderException.MalformedResponse(UNKNOWN_FORMAT)
        return entries.mapNotNull(::voiceOf)
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }

    private fun voiceOf(entry: VoiceEntry): SpeechVoice? {
        val id = entry.id?.takeIf { it.isNotBlank() } ?: return null
        return SpeechVoice(id, entry.name?.takeIf { it.isNotBlank() } ?: id)
    }

    private companion object {
        const val VOICES_PATH = "voices"
        const val ERROR_PREVIEW_BYTES = 300L
        const val UNKNOWN_FORMAT = "Server returned the voice list in an unknown format"
    }
}
