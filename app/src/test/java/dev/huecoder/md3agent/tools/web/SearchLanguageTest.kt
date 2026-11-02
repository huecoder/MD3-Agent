package dev.huecoder.md3agent.tools.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SearchLanguageTest {
    @Test
    fun `the app language picks the search region`() {
        assertEquals("ru-ru", SearchLanguage.duckDuckGoRegion(Locale.forLanguageTag("ru")))
        assertEquals("br-pt", SearchLanguage.duckDuckGoRegion(Locale.forLanguageTag("pt-BR")))
        assertEquals("id-id", SearchLanguage.duckDuckGoRegion(Locale.forLanguageTag("id")))
    }

    @Test
    fun `a language the app does not support leaves the engine default`() {
        assertNull(SearchLanguage.duckDuckGoRegion(Locale.FRENCH))
        assertNull(SearchLanguage.braveLanguage(Locale.forLanguageTag("id")))
    }

    @Test
    fun `brave gets its own language codes`() {
        assertEquals("pt-br", SearchLanguage.braveLanguage(Locale.forLanguageTag("pt-BR")))
        assertEquals("ru", SearchLanguage.braveLanguage(Locale.forLanguageTag("ru-RU")))
    }
}
