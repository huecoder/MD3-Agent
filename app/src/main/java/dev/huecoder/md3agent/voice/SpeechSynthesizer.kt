package dev.huecoder.md3agent.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.huecoder.md3agent.data.settings.VoiceProvider
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.llm.ProviderEndpoint
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.wire.GeminiGenerate
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.fetchToFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

sealed interface PreparedSpeech {
    data class Text(val text: String) : PreparedSpeech

    data class Audio(val file: File) : PreparedSpeech
}

interface SpeechSynthesizer {
    suspend fun prepare(text: String): PreparedSpeech

    suspend fun play(speech: PreparedSpeech)

    fun stop()

    fun release()
}

class SystemSpeechSynthesizer(context: Context, private val language: String) : SpeechSynthesizer {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, Continuation<Unit>>()
    private val engine = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    init {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) = finish(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) = finish(utteranceId)

                override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId)
            },
        )
    }

    override suspend fun prepare(text: String): PreparedSpeech = PreparedSpeech.Text(text)

    override suspend fun play(speech: PreparedSpeech) {
        if (speech !is PreparedSpeech.Text) return
        if (!ready.await()) throw IOException("System text-to-speech is unavailable")
        if (language.isNotBlank()) engine.language = Locale.forLanguageTag(language)
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { continuation ->
            pending[id] = continuation
            continuation.invokeOnCancellation { pending.remove(id); engine.stop() }
            if (engine.speak(speech.text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR) finish(id)
        }
    }

    override fun stop() {
        engine.stop()
        pending.keys.toList().forEach(::finish)
    }

    override fun release() {
        stop()
        engine.shutdown()
    }

    private fun finish(utteranceId: String) {
        pending.remove(utteranceId)?.resume(Unit)
    }
}

class ProviderSpeechSynthesizer(
    private val httpClients: HttpClientProvider,
    private val provider: VoiceProvider,
    private val cacheDir: File,
) : SpeechSynthesizer {
    private var player: MediaPlayer? = null

    private val endpoint by lazy { ProviderEndpoint(provider.asEndpointProfile()) }

    override suspend fun prepare(text: String): PreparedSpeech = when (provider.format) {
        ApiFormat.GEMINI -> gemini(text)
        else -> openAi(text)
    }

    private suspend fun openAi(text: String): PreparedSpeech {
        val body = buildJsonObject {
            put("model", provider.speechModel)
            put("voice", provider.speechVoice)
            put("input", text)
            put("response_format", RESPONSE_FORMAT)
        }.toString().toRequestBody(JSON)
        val request = endpoint.authorizedRequest(SPEECH_PATH).post(body).build()
        return withTempFile(".$RESPONSE_FORMAT") { target -> PreparedSpeech.Audio(httpClients.current().fetchToFile(request, target)) }
    }

    private suspend fun gemini(text: String): PreparedSpeech = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonArray("contents") { addJsonObject { putJsonArray("parts") { addJsonObject { put("text", text) } } } }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", provider.speechVoice) } }
                }
            }
        }
        val request = GeminiGenerate.request(endpoint, provider.speechModel, body)
        val response = withTempFile(".json") { target ->
            httpClients.current().fetchToFile(request, target).readText().also { target.delete() }
        }
        val audio = GeminiGenerate.inlineData(GeminiGenerate.parts(response))
            ?: throw ProviderException.MalformedResponse("Gemini returned no audio")
        withTempFile(".wav") { target ->
            PreparedSpeech.Audio(PcmWav.write(Base64.getDecoder().decode(audio.base64), PcmWav.sampleRateOf(audio.mimeType), target))
        }
    }

    private inline fun <T> withTempFile(suffix: String, block: (File) -> T): T {
        val target = File.createTempFile(FILE_PREFIX, suffix, cacheDir)
        return try {
            block(target)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    override suspend fun play(speech: PreparedSpeech) {
        if (speech !is PreparedSpeech.Audio) return
        try {
            suspendCancellableCoroutine { continuation ->
                val media = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(speech.file.absolutePath)
                    setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                    setOnErrorListener { _, _, _ -> if (continuation.isActive) continuation.resume(Unit); true }
                    prepare()
                    start()
                }
                player = media
                continuation.invokeOnCancellation { media.stop() }
            }
        } finally {
            player?.release()
            player = null
            speech.file.delete()
        }
    }

    override fun stop() {
        runCatching { player?.stop() }
    }

    override fun release() = stop()

    private companion object {
        const val SPEECH_PATH = "audio/speech"
        const val RESPONSE_FORMAT = "mp3"
        const val FILE_PREFIX = "speech"
        val JSON = "application/json".toMediaType()
    }
}
