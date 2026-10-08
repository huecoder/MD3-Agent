package dev.huecoder.md3assistant.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3assistant.conversation.ChatActivity
import dev.huecoder.md3assistant.conversation.ConversationRunner
import dev.huecoder.md3assistant.data.chat.ChatRepository
import dev.huecoder.md3assistant.data.db.ChatSummary
import dev.huecoder.md3assistant.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ChatAge { TODAY, YESTERDAY, THIS_WEEK, EARLIER }

data class ChatGroup(val age: ChatAge, val chats: List<ChatSummary>)

data class HomeUiState(
    val groups: List<ChatGroup> = emptyList(),
    val activeModel: String? = null,
    val hasProvider: Boolean = true,
    val isLoaded: Boolean = false,
    val activity: Map<String, ChatActivity> = emptyMap(),
)

class HomeViewModel(
    private val chats: ChatRepository,
    settings: SettingsRepository,
    runner: ConversationRunner,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(chats.observeSummaries(), settings.settings, runner.activity) { summaries, current, activity ->
        HomeUiState(
            activity = activity,
            groups = group(summaries),
            activeModel = current.activeSelection?.modelId,
            hasProvider = current.providers.isNotEmpty(),
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun delete(chatId: String) {
        viewModelScope.launch { chats.delete(chatId) }
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
