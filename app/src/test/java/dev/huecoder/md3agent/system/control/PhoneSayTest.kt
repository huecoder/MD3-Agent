package dev.huecoder.md3agent.system.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSayTest {
    @Test
    fun `a plain phrase passes through`() {
        assertEquals("Opening Settings to find Wi-Fi", PhoneSay.clean("Opening Settings to find Wi-Fi"))
    }

    @Test
    fun `nothing in, nothing out`() {
        assertNull(PhoneSay.clean(null))
        assertNull(PhoneSay.clean("   \n  "))
        assertNull(PhoneSay.clean("**"))
    }

    @Test
    fun `markdown is removed`() {
        assertEquals("Tapping Wi-Fi", PhoneSay.clean("**Tapping** `Wi-Fi`"))
        assertEquals("Opening settings", PhoneSay.clean("# Opening settings"))
        assertEquals("Opening settings", PhoneSay.clean("- Opening settings"))
        assertEquals("the guide", PhoneSay.clean("[the guide](https://example.com/a_b)"))
        assertEquals("Scrolling down", PhoneSay.clean("_Scrolling down_"))
    }

    @Test
    fun `underscores inside words stay`() {
        assertEquals("Opening wifi_settings", PhoneSay.clean("Opening wifi_settings"))
    }

    @Test
    fun `lines collapse into one`() {
        assertEquals("Opening Settings now", PhoneSay.clean("Opening\n  Settings\nnow"))
    }

    @Test
    fun `wrapping quotes are dropped`() {
        assertEquals("Opening Settings", PhoneSay.clean("\"Opening Settings\""))
        assertEquals("Открываю настройки", PhoneSay.clean("«Открываю настройки»"))
    }

    @Test
    fun `a long phrase is cut at a word and marked`() {
        val long = "I am going to open the settings app and look for the wifi network list now"

        val cleaned = PhoneSay.clean(long)!!

        assertTrue(cleaned.length <= PhoneSay.MAX_CHARS)
        assertTrue(cleaned.endsWith("…"))
        assertTrue(long.startsWith(cleaned.removeSuffix("…")))
        assertTrue(cleaned.removeSuffix("…").last() != ' ')
    }

    @Test
    fun `a long word without spaces is still cut`() {
        val cleaned = PhoneSay.clean("x".repeat(200))!!

        assertEquals(PhoneSay.MAX_CHARS, cleaned.length)
        assertTrue(cleaned.endsWith("…"))
    }

    @Test
    fun `a phrase of exactly the limit is kept whole`() {
        val exact = "a".repeat(PhoneSay.MAX_CHARS)

        assertEquals(exact, PhoneSay.clean(exact))
    }
}
