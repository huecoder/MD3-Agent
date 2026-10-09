package dev.huecoder.md3agent.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.chat.SearchHistory
import dev.huecoder.md3agent.data.chat.SearchSnippet
import dev.huecoder.md3agent.data.db.MessageRole
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class ChatResult(val chatId: String, val title: String, val look: ChatLook, val match: IntRange?)

data class MessageResult(
    val messageId: String,
    val chatId: String,
    val chatTitle: String,
    val fromUser: Boolean,
    val createdAt: Long,
    val snippet: SearchSnippet,
)

data class SearchResults(val query: String = "", val chats: List<ChatResult> = emptyList(), val messages: List<MessageResult> = emptyList()) {
    val isEmpty: Boolean get() = chats.isEmpty() && messages.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ChatSearchViewModel(private val chats: ChatRepository, private val history: SearchHistory) : ViewModel() {
    private val query = MutableStateFlow("")

    val recent: StateFlow<List<String>> = history.entries

    val results: StateFlow<SearchResults> = query
        .debounce(DEBOUNCE_MS)
        .mapLatest { text -> if (text.isBlank()) SearchResults() else search(text.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchResults())

    fun setQuery(text: String) {
        query.value = text
    }

    fun remember(text: String) = history.add(text)

    fun forget(text: String) = history.remove(text)

    private suspend fun search(text: String): SearchResults {
        val titled = chats.observeSummaries().first()
            .filter { it.trashedAt == null && it.title.contains(text, ignoreCase = true) }
            .take(CHAT_LIMIT)
            .map { chat ->
                val at = chat.title.indexOf(text, ignoreCase = true)
                ChatResult(chat.id, chat.title, ChatLook.of(chat.iconKey, chat.colorKey, chat.shapeKey), at until at + text.length)
            }
        val messages = chats.searchMessages(text, MESSAGE_LIMIT).map { hit ->
            MessageResult(
                messageId = hit.messageId,
                chatId = hit.chatId,
                chatTitle = hit.chatTitle,
                fromUser = hit.role == MessageRole.USER,
                createdAt = hit.createdAt,
                snippet = SearchSnippet.around(hit.content, text, SNIPPET_WIDTH),
            )
        }
        return SearchResults(text, titled, messages)
    }

    private companion object {
        const val DEBOUNCE_MS = 200L
        const val STOP_TIMEOUT_MS = 5_000L
        const val CHAT_LIMIT = 8
        const val MESSAGE_LIMIT = 40
        const val SNIPPET_WIDTH = 120
    }
}
