package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.HttpStatusException
import dev.huecoder.md3agent.net.fetchText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException

data class SpeechVoice(val id: String, val name: String)

class VoiceListClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(provider: ProviderProfile): List<SpeechVoice> {
        if (provider.format != ApiFormat.ELEVENLABS) return emptyList()
        val request = ProviderEndpoint(provider).authorizedRequest(VOICES_PATH).get().build()
        val body = try {
            httpClients.current().fetchText(request)
        } catch (e: HttpStatusException) {
            throw ProviderException.Http(e.code, e.message.orEmpty())
        } catch (e: IOException) {
            throw ProviderException.Network(e)
        }
        return parse(body)
    }

    internal fun parse(raw: String): List<SpeechVoice> {
        val voices = try {
            json.parseToJsonElement(raw).jsonObject["voices"] as? JsonArray
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw ProviderException.MalformedResponse("Server returned the voice list in an unknown format")
        return voices.mapNotNull { (it as? JsonObject)?.let(::voiceOf) }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }

    private fun voiceOf(entry: JsonObject): SpeechVoice? {
        val id = entry.text("voice_id") ?: return null
        return SpeechVoice(id, entry.text("name") ?: id)
    }

    private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val VOICES_PATH = "voices"
    }
}
