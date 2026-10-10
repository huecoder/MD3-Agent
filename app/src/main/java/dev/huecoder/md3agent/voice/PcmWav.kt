package dev.huecoder.md3agent.voice

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcmWav {
    const val DEFAULT_SAMPLE_RATE = 24_000
    private const val HEADER_BYTES = 44
    private const val FORMAT_CHUNK_BYTES = 16
    private const val PCM_FORMAT: Short = 1
    private const val CHANNELS: Short = 1
    private const val BITS_PER_SAMPLE: Short = 16
    private const val BYTES_PER_SAMPLE = 2
    private val RATE_PATTERN = Regex("rate=(\\d+)")

    fun sampleRateOf(mimeType: String): Int = RATE_PATTERN.find(mimeType)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE

    fun write(pcm: ByteArray, sampleRate: Int, target: File): File {
        target.outputStream().use { output ->
            output.write(header(pcm.size, sampleRate))
            output.write(pcm)
        }
        return target
    }

    fun header(dataBytes: Int, sampleRate: Int): ByteArray = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(HEADER_BYTES - 8 + dataBytes)
        put("WAVE".toByteArray())
        put("fmt ".toByteArray())
        putInt(FORMAT_CHUNK_BYTES)
        putShort(PCM_FORMAT)
        putShort(CHANNELS)
        putInt(sampleRate)
        putInt(sampleRate * CHANNELS * BYTES_PER_SAMPLE)
        putShort((CHANNELS * BYTES_PER_SAMPLE).toShort())
        putShort(BITS_PER_SAMPLE)
        put("data".toByteArray())
        putInt(dataBytes)
    }.array()
}
