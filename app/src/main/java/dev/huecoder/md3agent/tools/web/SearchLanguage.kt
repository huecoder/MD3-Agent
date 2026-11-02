package dev.huecoder.md3agent.tools.web

import java.util.Locale

internal object SearchLanguage {
    private val DUCKDUCKGO_REGIONS = mapOf(
        "en" to "us-en",
        "ru" to "ru-ru",
        "es" to "es-es",
        "pt" to "br-pt",
        "vi" to "vn-vi",
        "id" to "id-id",
        "tr" to "tr-tr",
        "de" to "de-de",
    )
    private val BRAVE_LANGUAGES = mapOf(
        "en" to "en",
        "ru" to "ru",
        "es" to "es",
        "pt" to "pt-br",
        "vi" to "vi",
        "tr" to "tr",
        "de" to "de",
    )

    fun duckDuckGoRegion(locale: Locale): String? = DUCKDUCKGO_REGIONS[primary(locale)]

    fun braveLanguage(locale: Locale): String? = BRAVE_LANGUAGES[primary(locale)]

    private fun primary(locale: Locale): String = locale.toLanguageTag().substringBefore('-')
}
