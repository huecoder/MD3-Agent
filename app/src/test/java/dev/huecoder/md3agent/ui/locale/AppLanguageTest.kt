package dev.huecoder.md3agent.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun `the system language picks its twin and anything else falls back to english`() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(Locale.forLanguageTag("ru-RU")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(Locale.forLanguageTag("en-GB")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(Locale.forLanguageTag("fr-FR")))
    }

    @Test
    fun `every shipped translation is picked by its region variants`() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.of(Locale.forLanguageTag("de-AT")))
        assertEquals(AppLanguage.SPANISH, AppLanguage.of(Locale.forLanguageTag("es-MX")))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.of(Locale.forLanguageTag("pt-PT")))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.of(Locale.forLanguageTag("id-ID")))
        assertEquals(AppLanguage.TURKISH, AppLanguage.of(Locale.forLanguageTag("tr-TR")))
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.of(Locale.forLanguageTag("vi-VN")))
    }

    @Test
    fun `stepping wraps around the list`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.RUSSIAN.next(1))
        assertEquals(AppLanguage.GERMAN, AppLanguage.RUSSIAN.next(-1))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.GERMAN.next(1))
    }
}
