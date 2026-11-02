package dev.huecoder.md3agent.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.conversation.ConversationRunner
import dev.huecoder.md3agent.conversation.NoModelConfiguredException
import dev.huecoder.md3agent.conversation.StreamingDraft
import dev.huecoder.md3agent.conversation.ToolApprovals
import dev.huecoder.md3agent.conversation.PendingPrompt
import dev.huecoder.md3agent.conversation.PromptAnswer
import dev.huecoder.md3agent.conversation.UserPrompts
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.attachments.AttachmentTooLargeException
import dev.huecoder.md3agent.data.chat.ChatBranch
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.db.ChatEntity
import dev.huecoder.md3agent.data.db.selection
import dev.huecoder.md3agent.data.files.ViewableFile
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.AgentSettings
import dev.huecoder.md3agent.data.settings.AttachMenuStyle
import dev.huecoder.md3agent.data.settings.ChatHeaderStyle
import dev.huecoder.md3agent.data.settings.ChatSettings
import dev.huecoder.md3agent.data.settings.ContextDisplay
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.llm.models.ModelFamily
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.settings.SpeakReplies
import dev.huecoder.md3agent.data.settings.TypingStyle
import dev.huecoder.md3agent.data.settings.VoiceSettings
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.voice.ReplySpeaker
import dev.huecoder.md3agent.voice.SpeechSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.IOException

data class ChatUiState(
    val chatId: String? = null,
    val title: UiText = NEW_CHAT_TITLE,
    val agentId: String? = null,
    val agentName: String = AgentProfile.DEFAULT_AGENT_NAME,
    val modelId: String? = null,
    val selection: ModelSelection? = null,
    val modelFamily: ModelFamily? = null,
    val modelName: String? = null,
    val messages: List<ChatItem> = emptyList(),
    val showTokenUsage: Boolean = false,
    val showContext: Boolean = false,
    val attachMenu: AttachMenuStyle = AttachMenuStyle.COMPACT,
    val header: ChatHeaderStyle = ChatHeaderStyle.COMPACT,
    val contextDisplay: ContextDisplay = ContextDisplay.NUMBERS,
    val contextTokens: Int? = null,
    val contextLimit: Int = ContextMeter.DEFAULT_LIMIT,
    val customContextLimit: Int? = null,
    val typing: TypingStyle = TypingStyle(),
    val isStreaming: Boolean = false,
    val pendingAttachments: List<Attachment> = emptyList(),
    val isImporting: Boolean = false,
    val look: ChatLook = ChatLook.Default,
    val focusItemId: String? = null,
) {
    val isStarted: Boolean get() = chatId != null

    val context: ContextMeter? get() = contextTokens?.takeIf { showContext }?.let { ContextMeter(it, contextLimit, customContextLimit) }

    fun canSend(text: String): Boolean = (text.isNotBlank() || pendingAttachments.isNotEmpty()) && !isImporting

    companion object {
        val NEW_CHAT_TITLE = UiText.Resource(R.string.home_new_chat)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    initialChatId: String?,
    focusMessageId: String?,
    private val chats: ChatRepository,
    private val runner: ConversationRunner,
    private val attachments: AttachmentStore,
    private val approvals: ToolApprovals,
    private val prompts: UserPrompts,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalog,
    private val appScope: CoroutineScope,
    synthesizers: (VoiceSettings, AgentProfile) -> SpeechSynthesizer,
    private val screenNote: () -> String? = { null },
) : ViewModel() {
    private val chatId = MutableStateFlow(initialChatId)
    private val pendingFocus = MutableStateFlow(focusMessageId)
    private val _errors = MutableStateFlow<UiText?>(null)
    private val _speechError = MutableStateFlow<String?>(null)
    private val pending = MutableStateFlow(PendingAttachments(items = restoredAttachments(initialChatId)))
    private var draftText = chats.draft(initialChatId).text
    private val pendingSelection = MutableStateFlow<ModelSelection?>(null)
    private val pendingAgentId = MutableStateFlow<String?>(null)

    val errors: StateFlow<UiText?> = _errors.asStateFlow()

    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    private val appSettings = settings.settings
    private val voice = appSettings.map { it.voice }.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceSettings())
    private val agents = appSettings.map { it.agents }.stateIn(viewModelScope, SharingStarted.Eagerly, AgentSettings())
    private val speaker = ReplySpeaker(viewModelScope) { synthesizers(voice.value, agents.value.forChat(state.value.agentId)) }
    private val narration = ReplyNarration(speaker)
    private var inputFromVoice = false

    val speakingId: StateFlow<String?> = speaker.speakingId

    val pendingPrompt: StateFlow<PendingPrompt?> = combine(chatId, prompts.pending) { id, asked -> id?.let(asked::get) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val state: StateFlow<ChatUiState> = chatId
        .flatMapLatest { id ->
            if (id == null) {
                combine(appSettings, pendingSelection, pendingAgentId) { current, chosen, chosenAgentId ->
                    val agent = current.agents.resolve(chosenAgentId)
                    ChatUiState(agentId = agent.id, agentName = agent.name).withChatSettings(current.chat).withSelection(chosen ?: current.activeSelection, current.chat)
                }
            } else {
                combine(chats.observeChat(id), chats.observeBranch(id), runner.drafts, appSettings, pendingFocus) { chat, branch, drafts, current, focus ->
                    val agent = current.agents.forChat(chat?.agentId)
                    buildState(id, chat, branch, drafts[id], focus)
                        .copy(agentId = agent.id, agentName = agent.name)
                        .withChatSettings(current.chat)
                        .withSelection(chat?.selection ?: current.activeSelection, current.chat)
                }
            }
        }
        .combine(pending) { state, attached -> state.copy(pendingAttachments = attached.items, isImporting = attached.importing > 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState(chatId = initialChatId))

    init {
        viewModelScope.launch {
            state.map { it.messages.lastOrNull() as? ChatItem.Reply }.filterNotNull().distinctUntilChanged().collect(narration::onLatest)
        }
        viewModelScope.launch {
            speaker.failures.collect { _speechError.value = it }
        }
    }

    fun noteDictation() {
        inputFromVoice = true
    }

    fun send(text: String) {
        val current = pending.value
        if ((text.isBlank() && current.items.isEmpty()) || current.importing > 0) return
        val attached = pending.value.items
        pending.update { it.copy(items = emptyList()) }
        if (shouldNarrate(fromVoice = inputFromVoice)) narration.arm(latestReplyId())
        inputFromVoice = false
        viewModelScope.launch {
            val id = chatId.value
            try {
                if (id == null) {
                    chatId.value = runner.startChat(text, attached, pendingSelection.value, state.value.agentId, screenNote()).id
                } else {
                    runner.send(id, text, attached)
                }
            } catch (e: NoModelConfiguredException) {
                pending.update { it.copy(items = attached + it.items) }
                persistDraft()
                _errors.value = UiText.Resource(R.string.chat_error_no_model)
            }
        }
    }

    fun edit(messageId: String, text: String) {
        val id = chatId.value ?: return
        if (pending.value.importing > 0) return
        val added = pending.value.items
        pending.update { it.copy(items = emptyList()) }
        if (shouldNarrate(fromVoice = inputFromVoice)) narration.arm(latestReplyId())
        inputFromVoice = false
        viewModelScope.launch { runner.edit(id, messageId, text, added) }
    }

    fun selectVersion(messageId: String) {
        val id = chatId.value ?: return
        viewModelScope.launch { chats.selectVersion(id, messageId) }
    }

    suspend fun contextBreakdown(): ContextBreakdown? {
        val id = chatId.value ?: return null
        val used = state.value.contextTokens ?: return null
        return runner.previewPrompt(id)?.let { ContextBreakdown.of(it.turns, it.tools, used) }
    }

    fun setContextWindow(tokens: Int?) {
        val selection = state.value.selection ?: return
        appScope.launch { settings.updateChat { it.withContextWindow(selection, tokens) } }
    }

    fun fork(reply: ChatItem.Reply, title: String, onForked: (String) -> Unit) {
        val id = chatId.value ?: return
        viewModelScope.launch { chats.fork(id, reply.id, title)?.let { onForked(it.id) } }
    }

    fun draft(): String = draftText

    fun saveDraft(text: String) {
        draftText = text
        persistDraft()
    }

    private fun persistDraft() = chats.saveDraft(chatId.value, draftText, pending.value.items)

    private fun restoredAttachments(id: String?): List<Attachment> =
        chats.draft(id).attachments.filter { attachments.file(it).exists() }

    fun rename(title: String) {
        val id = chatId.value ?: return
        viewModelScope.launch { chats.renameByUser(id, title) }
    }

    fun setLook(look: ChatLook) {
        val id = chatId.value ?: return
        viewModelScope.launch { chats.setLook(id, look) }
    }

    fun delete() {
        val id = chatId.value ?: return
        runner.cancel(id)
        appScope.launch { chats.trash(setOf(id)) }
    }

    fun selectModel(selection: ModelSelection) {
        val id = chatId.value
        if (id == null) pendingSelection.value = selection else viewModelScope.launch { chats.setModel(id, selection) }
        appScope.launch { settings.setActiveSelection(selection) }
    }

    fun selectAgent(agentId: String) {
        val id = chatId.value
        if (id == null) pendingAgentId.value = agentId else viewModelScope.launch { chats.setAgent(id, agentId) }
    }

    private fun ChatUiState.withChatSettings(chat: ChatSettings) = copy(
        showTokenUsage = chat.showTokenUsage,
        showContext = chat.showContext,
        attachMenu = chat.attachMenu,
        header = chat.header,
        contextDisplay = chat.contextDisplay,
        typing = chat.typing,
    )

    private suspend fun ChatUiState.withSelection(selection: ModelSelection?, chat: ChatSettings): ChatUiState {
        if (selection == null) return this
        val info = catalog.infoLoaded(selection.providerId, selection.modelId)
        val customLimit = chat.contextWindow(selection)
        return copy(
            selection = selection,
            modelId = selection.modelId,
            modelFamily = info.family,
            modelName = info.displayName,
            contextLimit = ContextMeter.limitFor(customLimit ?: info.contextLength),
            customContextLimit = customLimit,
        )
    }

    fun attach(uris: List<Uri>): Job = attachAll(uris) { attachments.import(it) }

    fun attachFiles(files: List<ViewableFile>): Job =
        attachAll(files) { attachments.importLocal(it.file, forModel = true, name = it.name, mimeType = it.mimeType) }

    private fun <T> attachAll(sources: List<T>, importer: suspend (T) -> Attachment): Job {
        sources.forEach { pending.update { it.copy(importing = it.importing + 1) } }
        return viewModelScope.launch {
            sources.map { source -> launch { attachOne(source, importer) } }.joinAll()
        }
    }

    private suspend fun <T> attachOne(source: T, importer: suspend (T) -> Attachment) {
        try {
            val attachment = importer(source)
            pending.update { it.copy(items = it.items + attachment) }
            persistDraft()
        } catch (e: AttachmentTooLargeException) {
            _errors.value = UiText.Resource(R.string.chat_attachment_too_large, listOf(e.limitMb))
        } catch (e: IOException) {
            _errors.value = UiText.Resource(R.string.chat_attachment_failed)
        } catch (e: SecurityException) {
            _errors.value = UiText.Resource(R.string.chat_attachment_failed)
        } catch (e: IllegalArgumentException) {
            _errors.value = UiText.Resource(R.string.chat_attachment_image_unreadable)
        } finally {
            pending.update { it.copy(importing = it.importing - 1) }
        }
    }

    fun removeAttachment(attachment: Attachment) {
        pending.update { it.copy(items = it.items - attachment) }
        persistDraft()
        viewModelScope.launch { attachments.delete(listOf(attachment)) }
    }

    fun stop() {
        chatId.value?.let(runner::cancel)
        stopSpeaking()
    }

    fun answerPrompt(prompt: PendingPrompt, answer: PromptAnswer) = prompts.answer(prompt.id, answer)

    fun respondToApproval(call: ToolCallUi, decision: ApprovalDecision) {
        chatId.value?.let { approvals.respond(it, call.id, decision) }
    }

    fun regenerate(reply: ChatItem.Reply) {
        if (shouldNarrate(fromVoice = false)) narration.arm(latestReplyId())
        viewModelScope.launch { runner.regenerate(reply.anchor) }
    }

    fun continueReply() {
        val id = chatId.value ?: return
        if (shouldNarrate(fromVoice = false)) narration.arm(latestReplyId())
        runner.continueReply(id)
    }

    fun toggleSpeech(reply: ChatItem.Reply) {
        if (speakingId.value == reply.id) return stopSpeaking()
        narration.disarm()
        speaker.speak(reply.id, reply.spokenText())
    }

    private fun stopSpeaking() {
        narration.disarm()
        speaker.stop()
    }

    private fun shouldNarrate(fromVoice: Boolean) = when (voice.value.speakReplies) {
        SpeakReplies.NEVER -> false
        SpeakReplies.AFTER_VOICE_INPUT -> fromVoice
        SpeakReplies.ALWAYS -> true
    }

    private fun latestReplyId(): String? = (state.value.messages.lastOrNull() as? ChatItem.Reply)?.id

    fun consumeError() {
        _errors.value = null
    }

    fun consumeSpeechError() {
        _speechError.value = null
    }

    private fun buildState(id: String, chat: ChatEntity?, branch: ChatBranch, draft: StreamingDraft?, focus: String?): ChatUiState {
        val items = ChatTimeline.build(branch.messages, draft, branch.versions)
        return ChatUiState(
            chatId = id,
            title = chat?.title?.let(UiText::Raw) ?: ChatUiState.NEW_CHAT_TITLE,
            modelId = chat?.modelId,
            look = chat?.let { ChatLook.of(it.iconKey, it.colorKey, it.shapeKey) } ?: ChatLook.Default,
            messages = items,
            contextTokens = ContextMeter.usedBy(branch.messages),
            isStreaming = draft != null,
            focusItemId = focus?.let { ChatTimeline.itemFor(it, branch.messages, items) },
        )
    }

    fun focusShown() {
        pendingFocus.value = null
    }

    override fun onCleared() {
        speaker.stop()
    }

    private data class PendingAttachments(val items: List<Attachment> = emptyList(), val importing: Int = 0)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
