package dev.huecoder.md3agent.voice

import android.media.AudioAttributes
import android.media.MediaPlayer
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.fetchToFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface PreviewState {
    val url: String?

    data object Idle : PreviewState {
        override val url: String? = null
    }

    data class Loading(override val url: String) : PreviewState

    data class Playing(override val url: String) : PreviewState

    data class Failed(override val url: String) : PreviewState
}

class VoicePreviewPlayer(
    private val httpClients: HttpClientProvider,
    private val cacheDir: File,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val state: StateFlow<PreviewState> = _state.asStateFlow()
    private var job: Job? = null

    fun toggle(url: String) {
        val current = _state.value
        val alreadyRunning = current.url == url && (current is PreviewState.Loading || current is PreviewState.Playing)
        stop()
        if (alreadyRunning) return
        job = scope.launch {
            _state.value = PreviewState.Loading(url)
            _state.value = try {
                val sample = download(url)
                withContext(Dispatchers.Main) { play(sample, url) }
                PreviewState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                PreviewState.Failed(url)
            } catch (e: IllegalStateException) {
                PreviewState.Failed(url)
            } catch (e: IllegalArgumentException) {
                PreviewState.Failed(url)
            } catch (e: SecurityException) {
                PreviewState.Failed(url)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = PreviewState.Idle
    }

    private suspend fun download(url: String): File {
        val address = url.toHttpUrlOrNull() ?: throw IOException("Invalid preview address")
        val directory = File(cacheDir, DIRECTORY).also { it.mkdirs() }
        val sample = File(directory, "${fingerprint(url)}.audio")
        if (sample.length() > 0) return sample
        val partial = File(directory, "${sample.name}.part")
        try {
            httpClients.current().fetchToFile(Request.Builder().url(address).build(), partial, MAX_SAMPLE_BYTES)
            if (!partial.renameTo(sample)) throw IOException("Preview could not be saved")
        } finally {
            partial.delete()
        }
        return sample
    }

    private suspend fun play(sample: File, url: String) {
        val player = MediaPlayer()
        try {
            suspendCancellableCoroutine { continuation ->
                player.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                player.setDataSource(sample.path)
                player.setOnPreparedListener {
                    _state.value = PreviewState.Playing(url)
                    it.start()
                }
                player.setOnCompletionListener { continuation.resume(Unit) }
                player.setOnErrorListener { _, _, _ ->
                    continuation.resumeWithException(IOException("Preview could not be played"))
                    true
                }
                player.prepareAsync()
            }
        } finally {
            player.release()
        }
    }

    private fun fingerprint(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(FINGERPRINT_LENGTH)

    private companion object {
        const val DIRECTORY = "voice-previews"
        const val MAX_SAMPLE_BYTES = 4L * 1024 * 1024
        const val FINGERPRINT_LENGTH = 32
    }
}
