package dev.huecoder.md3agent.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start() {
        cancel()
        val file = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, context.cacheDir)
        val created = newMediaRecorder()
        try {
            created.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            created.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(SAMPLE_RATE_HZ)
            created.setAudioEncodingBitRate(BIT_RATE_BPS)
            created.setOutputFile(file)
            created.prepare()
            created.start()
        } catch (e: RuntimeException) {
            created.release()
            file.delete()
            throw IOException(MICROPHONE_UNAVAILABLE, e)
        }
        recorder = created
        output = file
    }

    fun level(): Float = (recorder?.maxAmplitude ?: 0).toFloat() / MAX_AMPLITUDE

    fun stop(): File {
        val active = recorder ?: throw IOException(NOT_RECORDING)
        val file = output ?: throw IOException(NOT_RECORDING)
        recorder = null
        output = null
        try {
            active.stop()
        } catch (e: RuntimeException) {
            file.delete()
            throw RecordingTooShortException(e)
        } finally {
            active.release()
        }
        return file
    }

    fun cancel() {
        recorder?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        output?.delete()
        recorder = null
        output = null
    }

    private fun newMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()

    companion object {
        const val MIME_TYPE = "audio/ogg"
        private const val FILE_PREFIX = "dictation-"
        private const val FILE_SUFFIX = ".ogg"
        private const val SAMPLE_RATE_HZ = 16_000
        private const val BIT_RATE_BPS = 24_000
        private const val MAX_AMPLITUDE = 32_767f
        private const val MICROPHONE_UNAVAILABLE = "Microphone is unavailable"
        private const val NOT_RECORDING = "Recording is not in progress"
    }
}

class RecordingTooShortException(cause: Throwable) : IOException("Recording is too short", cause)
