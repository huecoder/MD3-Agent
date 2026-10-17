package com.mwagent.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mwagent.conversation.ChatActivity
import com.mwagent.conversation.ConversationRunner
import com.mwagent.data.chat.ChatRepository
import com.mwagent.data.db.ChatSummary
import com.mwagent.data.settings.AgentSettings
import com.mwagent.data.db.FolderEntity
import com.mwagent.data.settings.ModelSelection
import com.mwagent.data.settings.SettingsRepository
import com.mwagent.data.settings.SwipeAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ChatAge { TODAY, YESTERDAY, THIS_WEEK, EARLIER }

data class ChatGroup(val age: ChatAge, val chats: List<ChatSummary>)

sealed interface ChatFilter {
    data object All : ChatFilter
    data class Folder(val id: String) : ChatFilter
    data object Archive : ChatFilter
    data object Trash : ChatFilter
}

sealed interface HomeNotice {
    val chatIds: Set<String>

    data class Trashed(override val chatIds: Set<String>) : HomeNotice
    data class Archived(override val chatIds: Set<String>) : HomeNotice
}

data class HomeUiState(
    val groups: List<ChatGroup> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val filter: ChatFilter = ChatFilter.All,
    val hasArchive: Boolean = false,
    val hasTrash: Boolean = false,
    val swipeRight: SwipeAction = SwipeAction.TRASH,
    val swipeLeft: SwipeAction = SwipeAction.ARCHIVE,
    val selection: Set<String> = emptySet(),
    val hasChats: Boolean = false,
    val hasProvider: Boolean = true,
    val isLoaded: Boolean = false,
    val activity: Map<String, ChatActivity> = emptyMap(),
    val agents: AgentSettings = AgentSettings(),
    val defaultSelection: ModelSelection? = null,
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()
    val visibleIds: List<String> get() = groups.flatMap { group -> group.chats.map { it.id } }
}

private data class Filters(val filter: ChatFilter, val selection: Set<String>)

class HomeViewModel(
    private val chats: ChatRepository,
    private val settings: SettingsRepository,
    runner: ConversationRunner,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val filter = MutableStateFlow<ChatFilter>(ChatFilter.All)
    private val _notices = Channel<HomeNotice>(Channel.BUFFERED)
    private var rangeBase: Set<String>? = null

    val notices: Flow<HomeNotice> = _notices.receiveAsFlow()
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val summaries = chats.observeSummaries()

    private val filters = combine(filter, selection, ::Filters)

    init {
        viewModelScope.launch { chats.trashEvents.collect { _notices.send(HomeNotice.Trashed(it)) } }
    }

    fun selectDefaultModel(selection: ModelSelection) {
        viewModelScope.launch { settings.setActiveSelection(selection) }
    }

    fun selectDefaultAgent(agentId: String) {
        viewModelScope.launch { settings.updateAgents { it.activate(agentId) } }
    }

    val state: StateFlow<HomeUiState> = combine(
        summaries,
        chats.observeFolders(),
        filters,
        settings.settings,
        runner.activity,
    ) { all, folders, filters, current, activity ->
        val shown = filters.filter.takeUnless { it is ChatFilter.Folder && folders.none { folder -> folder.id == it.id } } ?: ChatFilter.All
        val visible = all.filter { chat -> chat.belongsTo(shown) }
        HomeUiState(
            groups = group(visible),
            folders = folders,
            filter = shown,
            hasArchive = all.any { it.archivedAt != null && it.trashedAt == null },
            hasTrash = all.any { it.trashedAt != null },
            swipeRight = current.ui.swipeRight,
            swipeLeft = current.ui.swipeLeft,
            selection = filters.selection.intersect(visible.map { it.id }.toSet()),
            hasChats = all.isNotEmpty(),
            hasProvider = current.providers.isNotEmpty(),
            isLoaded = true,
            activity = activity,
            agents = current.agents,
            defaultSelection = current.activeSelection,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun setFilter(target: ChatFilter) {
        selection.update { emptySet() }
        filter.update { current -> if (current == target) ChatFilter.All else target }
    }

    fun toggleSelection(chatId: String) = selection.update { if (chatId in it) it - chatId else it + chatId }

    fun selectAll() = selection.update { state.value.visibleIds.toSet() }

    fun clearSelection() = selection.update { emptySet() }

    fun rename(chatId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { chats.renameByUser(chatId, title) }
    }

    fun selectRange(anchor: String, current: String) {
        val ids = state.value.visibleIds
        val from = ids.indexOf(anchor)
        val to = ids.indexOf(current)
        if (from < 0 || to < 0) return
        val base = rangeBase ?: selection.value.also { rangeBase = it }
        selection.value = base + ids.subList(minOf(from, to), maxOf(from, to) + 1)
    }

    fun endRange() {
        rangeBase = null
    }

    fun trash(chatIds: Set<String>) = update(chatIds) { chats.trash(it) }

    fun restore(chatIds: Set<String>) = update(chatIds) { chats.restore(it) }

    fun archive(chatIds: Set<String>) = update(chatIds) {
        chats.archive(it)
        _notices.send(HomeNotice.Archived(it))
    }

    fun unarchive(chatIds: Set<String>) = update(chatIds) { chats.unarchive(it) }

    fun deleteForever(chatIds: Set<String>) = update(chatIds) { chats.deleteForever(it) }

    fun emptyTrash() {
        viewModelScope.launch { chats.emptyTrash() }
    }

    fun undo(notice: HomeNotice) = when (notice) {
        is HomeNotice.Trashed -> restore(notice.chatIds)
        is HomeNotice.Archived -> unarchive(notice.chatIds)
    }

    private fun update(chatIds: Set<String>, change: suspend (Set<String>) -> Unit) {
        selection.update { it - chatIds }
        viewModelScope.launch { change(chatIds) }
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
        filter.update { if (it == ChatFilter.Folder(folderId)) ChatFilter.All else it }
        viewModelScope.launch { chats.deleteFolder(folderId) }
    }

    private fun ChatSummary.belongsTo(filter: ChatFilter): Boolean = when (filter) {
        ChatFilter.All -> trashedAt == null && archivedAt == null
        is ChatFilter.Folder -> trashedAt == null && archivedAt == null && folderId == filter.id
        ChatFilter.Archive -> trashedAt == null && archivedAt != null
        ChatFilter.Trash -> trashedAt != null
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
    }
}
