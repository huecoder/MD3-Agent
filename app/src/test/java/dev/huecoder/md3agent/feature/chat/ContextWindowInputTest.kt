package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextWindowInputTest {
    @Test
    fun `plain numbers and separators are read as tokens`() {
        assertEquals(262_144, ContextWindowInput.parse("262144"))
        assertEquals(262_144, ContextWindowInput.parse("262 144"))
        assertEquals(262_144, ContextWindowInput.parse("262,144"))
    }

    @Test
    fun `k and m suffixes in both alphabets scale the number`() {
        assertEquals(256_000, ContextWindowInput.parse("256k"))
        assertEquals(256_000, ContextWindowInput.parse("256К"))
        assertEquals(1_500_000, ContextWindowInput.parse("1,5m"))
        assertEquals(2_000_000, ContextWindowInput.parse("2 М"))
    }

    @Test
    fun `text and out of range values are rejected`() {
        assertNull(ContextWindowInput.parse(""))
        assertNull(ContextWindowInput.parse("big"))
        assertNull(ContextWindowInput.parse("12"))
        assertNull(ContextWindowInput.parse("1000m"))
    }
}
