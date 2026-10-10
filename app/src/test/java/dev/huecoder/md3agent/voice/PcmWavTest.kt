package dev.huecoder.md3agent.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWavTest {
    @Test
    fun `sample rate comes from the gemini mime type`() {
        assertEquals(24_000, PcmWav.sampleRateOf("audio/L16;codec=pcm;rate=24000"))
        assertEquals(16_000, PcmWav.sampleRateOf("audio/L16;rate=16000"))
        assertEquals(PcmWav.DEFAULT_SAMPLE_RATE, PcmWav.sampleRateOf("audio/L16"))
    }

    @Test
    fun `header describes 16 bit mono pcm of the given size`() {
        val header = ByteBuffer.wrap(PcmWav.header(dataBytes = 4_800, sampleRate = 24_000)).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(header.array(), 0, 4))
        assertEquals(36 + 4_800, header.getInt(4))
        assertEquals("WAVE", String(header.array(), 8, 4))
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(24_000, header.getInt(24))
        assertEquals(48_000, header.getInt(28))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", String(header.array(), 36, 4))
        assertEquals(4_800, header.getInt(40))
    }
}
