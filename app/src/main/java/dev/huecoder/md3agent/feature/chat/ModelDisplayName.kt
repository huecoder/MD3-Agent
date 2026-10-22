package dev.huecoder.md3agent.feature.chat

object ModelDisplayName {
    private val DATE_SUFFIX = Regex("[-@](?:\\d{8}|\\d{4}-\\d{2}-\\d{2})$")

    fun short(name: String): String = name.replace(DATE_SUFFIX, "").ifBlank { name }
}
