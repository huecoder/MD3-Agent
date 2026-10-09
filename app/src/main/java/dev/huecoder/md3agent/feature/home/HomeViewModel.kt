package dev.huecoder.md3agent.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.conversation.ChatActivity
import dev.huecoder.md3agent.conversation.ConversationRunner
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.db.ChatSummary
import dev.huecoder.md3agent.data.db.FolderEntity
import dev.huecoder.md3agent.data.settings.SettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ChatAge { TODAY, YESTERDAY, THIS_WEEK, EARLIER }

data class ChatGroup(val age: ChatAge, val chats: List<ChatSummary>)

data class HomeUiState(
    val groups: List<ChatGroup> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val folderFilter: String? = null,
    val query: String = "",
    val selection: Set<String> = emptySet(),
    val hasChats: Boolean = false,
    val hasProvider: Boolean = true,
    val isLoaded: Boolean = false,
    val activity: Map<String, ChatActivity> = emptyMap(),
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()
    val visibleIds: List<String> get() = groups.flatMap { group -> group.chats.map { it.id } }
}

private data class Filters(val query: String, val folderId: String?, val selection: Set<String>)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val chats: ChatRepository,
    settings: SettingsRepository,
    runner: ConversationRunner,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val folderFilter = MutableStateFlow<String?>(null)
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val summaries = chats.observeSummaries()

    private val searchMatches = combine(query.debounce(SEARCH_DEBOUNCE_MS), summaries) { text, _ -> text.trim() }
        .mapLatest { text -> if (text.isEmpty()) null else chats.searchChatIds(text) }

    private val filters = combine(query, folderFilter, selection, ::Filters)

    val state: StateFlow<HomeUiState> = combine(
        combine(summaries, searchMatches, ::Pair),
        chats.observeFolders(),
        filters,
        settings.settings,
        runner.activity,
    ) { (all, matches), folders, filter, current, activity ->
        val folderId = filter.folderId?.takeIf { id -> folders.any { it.id == id } }
        val visible = all.filter { chat ->
            (folderId == null || chat.folderId == folderId) && (matches == null || chat.id in matches)
        }
        HomeUiState(
            groups = group(visible),
            folders = folders,
            folderFilter = folderId,
            query = filter.query,
            selection = filter.selection.intersect(all.map { it.id }.toSet()),
            hasChats = all.isNotEmpty(),
            hasProvider = current.providers.isNotEmpty(),
            isLoaded = true,
            activity = activity,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun setQuery(text: String) = query.update { text }

    fun setFolderFilter(folderId: String?) = folderFilter.update { folderId }

    fun toggleSelection(chatId: String) = selection.update { if (chatId in it) it - chatId else it + chatId }

    fun selectAll() = selection.update { state.value.visibleIds.toSet() }

    fun clearSelection() = selection.update { emptySet() }

    fun rename(chatId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { chats.renameByUser(chatId, title) }
    }

    fun delete(chatIds: Set<String>) {
        selection.update { it - chatIds }
        viewModelScope.launch { chatIds.forEach { chats.delete(it) } }
    }

    fun move(chatIds: Set<String>, folderId: String?) {
        selection.update { emptySet() }
        viewModelScope.launch { chats.moveToFolder(chatIds, folderId) }
    }

    fun createFolder(name: String, moving: Set<String> = emptySet()) {
        if (name.isBlank()) return
        selection.update { emptySet() }
        viewModelScope.launch {
            val folder = chats.createFolder(name)
            if (moving.isNotEmpty()) chats.moveToFolder(moving, folder.id)
        }
    }

    fun renameFolder(folderId: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { chats.renameFolder(folderId, name) }
    }

    fun deleteFolder(folderId: String) {
        folderFilter.update { if (it == folderId) null else it }
        viewModelScope.launch { chats.deleteFolder(folderId) }
    }

    private fun group(summaries: List<ChatSummary>): List<ChatGroup> {
        val today = LocalDate.now(zone())
        return summaries
            .groupBy { ageOf(it.updatedAt, today) }
            .toSortedMap()
            .map { (age, items) -> ChatGroup(age, items) }
    }

    private fun ageOf(epochMillis: Long, today: LocalDate): ChatAge {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()
        return when {
            !date.isBefore(today) -> ChatAge.TODAY
            date == today.minusDays(1) -> ChatAge.YESTERDAY
            date.isAfter(today.minusDays(DAYS_IN_WEEK)) -> ChatAge.THIS_WEEK
            else -> ChatAge.EARLIER
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DAYS_IN_WEEK = 7L
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
