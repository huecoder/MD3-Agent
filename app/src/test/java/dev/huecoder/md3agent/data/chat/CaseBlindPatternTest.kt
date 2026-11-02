package dev.huecoder.md3agent.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class CaseBlindPatternTest {
    @Test
    fun `every letter matches both its cases, in any alphabet`() {
        assertEquals("*[пП][рР][иИ][вВ][еЕ][тТ]*", CaseBlindPattern.containing("  Привет "))
    }

    @Test
    fun `glob wildcards in the query are matched literally`() {
        assertEquals("*[*][?][[]*", CaseBlindPattern.containing("*?["))
    }

    @Test
    fun `digits and punctuation stay as they are`() {
        assertEquals("*100%-[xX]*", CaseBlindPattern.containing("100%-x"))
    }
}
