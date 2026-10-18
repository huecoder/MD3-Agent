package dev.huecoder.md3agent.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun `the system language picks its twin and anything else falls back to english`() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(Locale.forLanguageTag("ru-RU")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(Locale.forLanguageTag("en-GB")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(Locale.forLanguageTag("de-DE")))
    }

    @Test
    fun `stepping wraps around the list`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.RUSSIAN.next(1))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.ENGLISH.next(1))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.RUSSIAN.next(-1))
    }
}
