package dev.huecoder.md3assistant.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.conversation.ConversationRunner
import dev.huecoder.md3assistant.conversation.NoModelConfiguredException
import dev.huecoder.md3assistant.conversation.StreamingDraft
import dev.huecoder.md3assistant.data.attachments.Attachment
import dev.huecoder.md3assistant.data.attachments.AttachmentStore
import dev.huecoder.md3assistant.data.attachments.AttachmentTooLargeException
import dev.huecoder.md3assistant.data.attachments.UnsupportedAttachmentException
import dev.huecoder.md3assistant.data.chat.ChatRepository
import dev.huecoder.md3assistant.data.db.ChatEntity
import dev.huecoder.md3assistant.data.db.MessageEntity
import dev.huecoder.md3assistant.data.settings.AgentProfile
import dev.huecoder.md3assistant.data.settings.SettingsRepository
import dev.huecoder.md3assistant.ui.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class ChatUiState(
    val chatId: String? = null,
    val title: UiText = NEW_CHAT_TITLE,
    val agentName: String = AgentProfile.DEFAULT_AGENT_NAME,
    val modelId: String? = null,
    val messages: List<ChatItem> = emptyList(),
    val showTokenUsage: Boolean = false,
    val isStreaming: Boolean = false,
    val pendingAttachments: List<Attachment> = emptyList(),
    val isImporting: Boolean = false,
) {
    fun canSend(text: String): Boolean = (text.isNotBlank() || pendingAttachments.isNotEmpty()) && !isImporting

    companion object {
        val NEW_CHAT_TITLE = UiText.Resource(R.string.home_new_chat)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    initialChatId: String?,
    private val chats: ChatRepository,
    private val runner: ConversationRunner,
    private val attachments: AttachmentStore,
    settings: SettingsRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val chatId = MutableStateFlow(initialChatId)
    private val _errors = MutableStateFlow<UiText?>(null)
    private val pending = MutableStateFlow(PendingAttachments())

    val errors: StateFlow<UiText?> = _errors.asStateFlow()

    private val appSettings = settings.settings

    val state: StateFlow<ChatUiState> = chatId
        .flatMapLatest { id ->
            if (id == null) {
                appSettings.map { ChatUiState(modelId = it.activeSelection?.modelId, agentName = it.agent.name, showTokenUsage = it.chat.showTokenUsage) }
            } else {
                combine(chats.observeChat(id), chats.observeMessages(id), runner.drafts, appSettings) { chat, messages, drafts, current ->
                    buildState(id, chat, messages, drafts[id]).copy(agentName = current.agent.name, showTokenUsage = current.chat.showTokenUsage)
                }
            }
        }
        .combine(pending) { state, attached -> state.copy(pendingAttachments = attached.items, isImporting = attached.importing > 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState(chatId = initialChatId))

    fun send(text: String) {
        val current = pending.value
        if ((text.isBlank() && current.items.isEmpty()) || current.importing > 0) return
        val attached = pending.value.items
        pending.update { it.copy(items = emptyList()) }
        viewModelScope.launch {
            val id = chatId.value
            try {
                if (id == null) chatId.value = runner.startChat(text, attached).id else runner.send(id, text, attached)
            } catch (e: NoModelConfiguredException) {
                pending.update { it.copy(items = attached + it.items) }
                _errors.value = UiText.Resource(R.string.chat_error_no_model)
            }
        }
    }

    fun attach(uris: List<Uri>) {
        uris.forEach { uri ->
            pending.update { it.copy(importing = it.importing + 1) }
            viewModelScope.launch {
                try {
                    val attachment = attachments.import(uri)
                    pending.update { it.copy(items = it.items + attachment) }
                } catch (e: UnsupportedAttachmentException) {
                    _errors.value = UiText.Resource(R.string.chat_attachment_unsupported, listOf(e.fileName))
                } catch (e: AttachmentTooLargeException) {
                    _errors.value = UiText.Resource(R.string.chat_attachment_too_large, listOf(e.limitKb))
                } catch (e: IOException) {
                    _errors.value = UiText.Resource(R.string.chat_attachment_failed)
                } catch (e: IllegalArgumentException) {
                    _errors.value = UiText.Resource(R.string.chat_attachment_image_unreadable)
                } finally {
                    pending.update { it.copy(importing = it.importing - 1) }
                }
            }
        }
    }

    fun removeAttachment(attachment: Attachment) {
        pending.update { it.copy(items = it.items - attachment) }
        viewModelScope.launch { attachments.delete(listOf(attachment)) }
    }

    fun stop() {
        chatId.value?.let(runner::cancel)
    }

    fun regenerate(reply: ChatItem.Reply) {
        viewModelScope.launch { runner.regenerate(reply.anchor) }
    }

    fun consumeError() {
        _errors.value = null
    }

    private fun buildState(id: String, chat: ChatEntity?, messages: List<MessageEntity>, draft: StreamingDraft?) = ChatUiState(
        chatId = id,
        title = chat?.title?.let(UiText::Raw) ?: ChatUiState.NEW_CHAT_TITLE,
        modelId = chat?.modelId,
        messages = ChatTimeline.build(messages, draft),
        isStreaming = draft != null,
    )

    override fun onCleared() {
        val unsent = pending.value.items
        if (unsent.isNotEmpty()) appScope.launch { attachments.delete(unsent) }
    }

    private data class PendingAttachments(val items: List<Attachment> = emptyList(), val importing: Int = 0)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
