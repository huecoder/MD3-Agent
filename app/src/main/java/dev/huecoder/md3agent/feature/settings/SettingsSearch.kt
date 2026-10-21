package dev.huecoder.md3agent.feature.settings

data class SettingsSearchRow(val page: SettingsPage, val title: String, val path: String)

object SettingsSearch {
    fun filter(rows: List<SettingsSearchRow>, query: String): List<SettingsSearchRow> {
        val words = query.lowercase().split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return emptyList()
        return rows
            .filter { row -> "${row.title} ${row.path}".lowercase().let { text -> words.all(text::contains) } }
            .sortedBy { row -> if (row.title.lowercase().contains(words.first())) TITLE_MATCH else PATH_MATCH }
    }

    private const val TITLE_MATCH = 0
    private const val PATH_MATCH = 1
}
