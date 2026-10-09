package dev.huecoder.md3agent.data.chat

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchHistory(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun add(query: String) {
        val clean = query.trim().takeIf { it.isNotEmpty() } ?: return
        update(listOf(clean) + _entries.value.filterNot { it.equals(clean, ignoreCase = true) })
    }

    fun remove(query: String) = update(_entries.value - query)

    private fun update(entries: List<String>) {
        val kept = entries.take(LIMIT)
        _entries.value = kept
        prefs.edit { putString(KEY, kept.joinToString(SEPARATOR)) }
    }

    private fun read(): List<String> = prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val FILE = "search_history"
        const val KEY = "queries"
        const val SEPARATOR = "\n"
        const val LIMIT = 8
    }
}
