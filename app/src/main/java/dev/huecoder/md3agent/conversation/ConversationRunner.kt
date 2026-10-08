package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentContent
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.db.ChatEntity
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.settings.AppSettings
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ChatCompletionClient
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.CompletionOptions
import dev.huecoder.md3agent.llm.models.ModelFamily
import dev.huecoder.md3agent.llm.models.ModelOutput
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.llm.ToolCallAccumulator
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.prompt.SystemPromptRenderer
import dev.huecoder.md3agent.tools.ToolContext
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import dev.huecoder.md3agent.data.memory.MemoryStore

class ConversationRunner(
    private val chats: ChatRepository,
    private val settings: SettingsRepository,
    private val completions: ChatCompletionClient,
    private val systemPrompt: SystemPromptRenderer,
    private val tools: ToolRegistry,
    private val attachments: AttachmentStore,
    private val approvals: ToolApprovals,
    private val prompts: UserPrompts,
    private val catalog: ModelCatalog,
    private val generatedImages: GeneratedImages,
    private val memories: MemoryStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _drafts = MutableStateFlow<Map<String, StreamingDraft>>(emptyMap())

    val drafts: StateFlow<Map<String, StreamingDraft>> = _drafts.asStateFlow()

    val activity: Flow<Map<String, ChatActivity>> = combine(_drafts, prompts.pending) { drafts, asked ->
        drafts.mapValues { (chatId, draft) ->
            if (draft.awaitingCallId != null || chatId in asked) ChatActivity.NEEDS_INPUT else ChatActivity.WORKING
        }
    }.distinctUntilChanged()

    init {
        scope.launch { chats.recoverInterruptedStreams() }
    }

    suspend fun startChat(text: String, attached: List<Attachment>, selection: ModelSelection? = null): ChatEntity {
        val chosen = selection ?: settings.current().activeSelection ?: throw NoModelConfiguredException()
        val chat = chats.createChat(titleFrom(text, attached), chosen)
        send(chat.id, text, attached)
        return chat
    }

    suspend fun send(chatId: String, text: String, attached: List<Attachment>) {
        chats.appendUserMessage(chatId, text.trim(), attached, systemPrompt.contextNote())
        launchReply(chatId)
    }

    suspend fun regenerate(assistantMessage: MessageEntity) {
        cancel(assistantMessage.chatId)
        val history = chats.history(assistantMessage.chatId)
        val prompt = history.lastOrNull { it.role == MessageRole.USER && it.createdAt < assistantMessage.createdAt } ?: return
        chats.truncateAfter(prompt)
        launchReply(assistantMessage.chatId)
    }

    fun cancel(chatId: String) {
        jobs.remove(chatId)?.cancel()
    }

    fun cancelAll() {
        jobs.keys.toList().forEach(::cancel)
    }

    private fun launchReply(chatId: String) {
        cancel(chatId)
        val job = scope.launch(start = CoroutineStart.LAZY) { generateReply(chatId) }
        jobs[chatId] = job
        job.invokeOnCompletion { jobs.remove(chatId, job) }
        job.start()
    }

    private suspend fun generateReply(chatId: String) {
        val current = settings.current()
        val model = resolveModel(chatId, current) ?: return chats.save(errorMessage(chatId, NO_MODEL_SELECTED))
        try {
            while (true) {
                val round = streamRound(chatId, model, current) ?: return
                if (round.toolCalls.isEmpty()) return
                val endsTurn = runTools(chatId, round, current)
                if (endsTurn) return
            }
        } finally {
            val self = currentCoroutineContext()[Job]
            _drafts.update { drafts -> if (jobs[chatId].let { it == null || it === self }) drafts - chatId else drafts }
        }
    }

    private suspend fun streamRound(chatId: String, model: ResolvedModel, current: AppSettings): StreamingDraft? {
        tools.awaitReady()
        val info = catalog.infoLoaded(model.provider.id, model.modelId)
        val traits = info.traits
        val options = CompletionOptions(
            reasoningEffort = model.reasoningEffort?.takeIf { traits.reasoning },
            reasoningControl = traits.reasoningControl,
            output = traits.output,
            explicitCaching = info.family == ModelFamily.CLAUDE,
        )
        val toolSpecs = if (traits.output == ModelOutput.IMAGE || info.toolUse == false) emptyList() else tools.specs(current.tools)
        val turns = buildTurns(current, chats.history(chatId))
        val message = chats.startAssistantMessage(chatId, model.modelId)
        val accumulator = ToolCallAccumulator()
        var draft = StreamingDraft(message.id, content = "", reasoning = "", toolCalls = emptyList(), runningCallIds = emptySet(), awaitingCallId = null, usage = null)
        publish(chatId, draft)
        try {
            completions.stream(model.provider, model.modelId, turns, toolSpecs, options).collect { event ->
                draft = when (event) {
                    is CompletionEvent.ContentDelta -> draft.copy(content = draft.content + event.text)
                    is CompletionEvent.ReasoningDelta -> draft.copy(reasoning = draft.reasoning + event.text)
                    is CompletionEvent.ImageOutput -> draft.copy(images = draft.images + event.url)
                    is CompletionEvent.ToolCallDelta -> draft.copy(toolCalls = accumulator.apply { add(event) }.calls())
                    is CompletionEvent.Finished -> draft
                    is CompletionEvent.Usage -> draft.copy(usage = event.usage)
                }
                publish(chatId, draft)
            }
            persist(message, draft, MessageStatus.COMPLETE, errorText = null)
            return draft
        } catch (e: CancellationException) {
            withContext(NonCancellable) { persist(message, draft, MessageStatus.CANCELLED, errorText = null) }
            throw e
        } catch (e: Exception) {
            persist(message, draft, MessageStatus.ERROR, errorText = e.message ?: e.javaClass.simpleName)
            return null
        }
    }

    private suspend fun runTools(chatId: String, round: StreamingDraft, current: AppSettings): Boolean {
        for (call in round.toolCalls) {
            val outcome = if (isApproved(chatId, round, call, current.tools)) {
                publish(chatId, round.copy(runningCallIds = setOf(call.id)))
                tools.run(call, current.tools, ToolContext(chatId))
            } else {
                ToolOutcome.denied()
            }
            chats.appendToolResult(chatId, call.id, outcome.content, outcome.messageStatus, outcome.attachments)
        }
        return round.toolCalls.all(tools::endsTurn)
    }

    private suspend fun isApproved(chatId: String, round: StreamingDraft, call: ToolCall, settings: ToolSettings): Boolean {
        if (tools.permission(call.name, settings) != ToolPermission.ASK || approvals.isAllowedForSession(chatId, call.name)) return true
        publish(chatId, round.copy(awaitingCallId = call.id))
        return approvals.request(chatId, call)
    }

    private suspend fun buildTurns(current: AppSettings, history: List<MessageEntity>): List<ChatTurn> {
        val contents = mutableMapOf<String, AttachmentContent>()
        history.flatMap { Attachment.decode(it.attachmentsJson) }.forEach { attachment ->
            attachments.contentFor(attachment)?.let { contents[attachment.id] = it }
        }
        return TurnAssembler.assemble(systemPrompt.render(current.agent, tools.promptNotes(current.tools), memories.instant()), history, contents)
    }

    private suspend fun resolveModel(chatId: String, current: AppSettings): ResolvedModel? {
        val chat = chats.chat(chatId)
        val chatProvider = current.provider(chat?.providerId)
        if (chatProvider != null && !chat?.modelId.isNullOrBlank()) {
            return ResolvedModel(chatProvider, chat.modelId, chat.reasoningEffort?.let(ReasoningEffort::fromWire))
        }
        val active = current.activeSelection ?: return null
        val provider = current.provider(active.providerId) ?: return null
        return ResolvedModel(provider, active.modelId, active.reasoningEffort)
    }

    private suspend fun persist(message: MessageEntity, draft: StreamingDraft, status: MessageStatus, errorText: String?) {
        val images = if (draft.images.isEmpty()) emptyList() else generatedImages.store(draft.images)
        chats.save(
            message.copy(
                attachmentsJson = Attachment.encode(images),
                content = draft.content,
                reasoning = draft.reasoning.ifEmpty { null },
                status = status,
                errorText = errorText,
                toolCallsJson = TurnAssembler.encodeToolCalls(draft.toolCalls),
                finishedAt = clock(),
                promptTokens = draft.usage?.promptTokens,
                completionTokens = draft.usage?.completionTokens,
                cachedTokens = draft.usage?.cachedTokens?.takeIf { it > 0 },
            ),
        )
    }

    private suspend fun errorMessage(chatId: String, text: String): MessageEntity =
        chats.startAssistantMessage(chatId, modelId = "").copy(status = MessageStatus.ERROR, errorText = text)

    private fun publish(chatId: String, draft: StreamingDraft) = _drafts.update { it + (chatId to draft) }

    private fun titleFrom(text: String, attached: List<Attachment>): String {
        val singleLine = text.trim().replace(WHITESPACE, " ").ifEmpty { attached.firstOrNull()?.name.orEmpty() }
        return if (singleLine.length <= TITLE_MAX_CHARS) singleLine else singleLine.take(TITLE_MAX_CHARS).trimEnd() + "…"
    }

    private companion object {
        const val TITLE_MAX_CHARS = 48
        const val NO_MODEL_SELECTED = "No model selected. Add a provider in settings."
        val WHITESPACE = Regex("""\s+""")
    }
}

private val ToolOutcome.messageStatus: MessageStatus
    get() = when {
        isDenied -> MessageStatus.CANCELLED
        isError -> MessageStatus.ERROR
        else -> MessageStatus.COMPLETE
    }

enum class ChatActivity { WORKING, NEEDS_INPUT }

data class StreamingDraft(
    val messageId: String,
    val content: String,
    val reasoning: String,
    val toolCalls: List<ToolCall>,
    val runningCallIds: Set<String>,
    val awaitingCallId: String?,
    val usage: TokenUsage?,
    val images: List<String> = emptyList(),
)

private data class ResolvedModel(val provider: ProviderProfile, val modelId: String, val reasoningEffort: ReasoningEffort?)

class NoModelConfiguredException : IllegalStateException("Add a provider and choose a model in settings first")
