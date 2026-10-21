package dev.huecoder.md3agent.conversation

import android.util.Log
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentContent
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.db.ChatEntity
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.AppSettings
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ChatCompletionClient
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.CompletionOptions
import dev.huecoder.md3agent.llm.models.ModelFamily
import dev.huecoder.md3agent.llm.models.ModelInfo
import dev.huecoder.md3agent.llm.models.ModelOutput
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.ReasoningBlock
import dev.huecoder.md3agent.llm.STREAM_LOG_TAG
import dev.huecoder.md3agent.llm.ReasoningTrace
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.llm.ToolCallAccumulator
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.llm.carriesReasoning
import dev.huecoder.md3agent.llm.promptFingerprint
import dev.huecoder.md3agent.llm.rejectsReplayedReasoning
import dev.huecoder.md3agent.llm.withoutReasoning
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
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
    private val gate: ToolGate = ToolGate.Open,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val titler = ChatTitler(chats, completions, catalog)
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

    suspend fun startChat(
        text: String,
        attached: List<Attachment>,
        selection: ModelSelection? = null,
        agentId: String? = null,
        screenNote: String? = null,
    ): ChatEntity {
        val current = settings.current()
        val chosen = selection ?: current.activeSelection ?: throw NoModelConfiguredException()
        val chat = chats.createChat(titleFrom(text, attached), chosen, current.agent(agentId).id)
        send(chat.id, text, attached, screenNote)
        return chat
    }

    suspend fun send(chatId: String, text: String, attached: List<Attachment>, screenNote: String? = null) {
        chats.appendUserMessage(chatId, text.trim(), attached, systemPrompt.contextNote(screenNote))
        launchReply(chatId)
    }

    suspend fun edit(chatId: String, messageId: String, text: String, added: List<Attachment>) {
        chats.editUserMessage(messageId, text, added) ?: return
        launchReply(chatId)
    }

    suspend fun regenerate(assistantMessage: MessageEntity) {
        cancel(assistantMessage.chatId)
        val history = chats.history(assistantMessage.chatId)
        val prompt = history.lastOrNull { it.role == MessageRole.USER && it.createdAt < assistantMessage.createdAt } ?: return
        chats.truncateAfter(prompt)
        launchReply(assistantMessage.chatId)
    }

    fun continueReply(chatId: String) = launchReply(chatId, resuming = true)

    fun cancel(chatId: String) {
        jobs.remove(chatId)?.cancel()
    }

    fun cancelAll() {
        jobs.keys.toList().forEach(::cancel)
    }

    private fun launchReply(chatId: String, resuming: Boolean = false) {
        cancel(chatId)
        val job = scope.launch(start = CoroutineStart.LAZY) { generateReply(chatId, resuming) }
        jobs[chatId] = job
        job.invokeOnCompletion { jobs.remove(chatId, job) }
        job.start()
    }

    private suspend fun generateReply(chatId: String, resuming: Boolean) {
        val current = settings.current()
        val model = resolveModel(chatId, current) ?: return chats.save(errorMessage(chatId, NO_MODEL_SELECTED))
        val agent = current.agents.forChat(chats.chat(chatId)?.agentId)
        val toolSettings = agent.tools(current.tools)
        try {
            var failedRound = if (resuming) chats.history(chatId).lastOrNull()?.takeIf(::isFailedRound) else null
            var failures = 0
            while (true) {
                when (val outcome = streamRound(chatId, model, agent, toolSettings, failedRound)) {
                    is RoundOutcome.Failed -> {
                        val plan = retryPlan(outcome.failure, ++failures) ?: return
                        failedRound = outcome.row
                        waitBeforeRetry(chatId, outcome, failures, plan)
                    }
                    is RoundOutcome.Finished -> {
                        failures = 0
                        failedRound = null
                        if (outcome.draft.toolCalls.isEmpty()) return labelLater(chatId, model, current)
                        val endsTurn = runTools(chatId, outcome.draft, toolSettings)
                        if (endsTurn) return
                    }
                }
            }
        } finally {
            val self = currentCoroutineContext()[Job]
            _drafts.update { drafts -> if (jobs[chatId].let { it == null || it === self }) drafts - chatId else drafts }
        }
    }

    private fun isFailedRound(message: MessageEntity) = message.role == MessageRole.ASSISTANT && message.status == MessageStatus.ERROR

    private suspend fun retryPlan(failure: Exception, attempt: Int): ReplyRetry.Plan? =
        ReplyRetry.plan(failure, attempt)?.takeIf { settings.current().chat.autoRetry }

    private suspend fun waitBeforeRetry(chatId: String, failed: RoundOutcome.Failed, attempt: Int, plan: ReplyRetry.Plan) {
        publish(chatId, failed.draft.copy(retry = RetryWait(attempt, plan.limit, clock() + plan.delayMs, failed.error)))
        delay(plan.delayMs)
    }

    private fun labelLater(chatId: String, model: ResolvedModel, current: AppSettings) {
        if (current.chat.autoTitle) scope.launch { titler.labelIfNeeded(chatId, model) }
    }

    private suspend fun streamRound(
        chatId: String,
        model: ResolvedModel,
        agent: AgentProfile,
        toolSettings: ToolSettings,
        resumed: MessageEntity?,
    ): RoundOutcome {
        tools.awaitReady()
        val info = catalog.infoLoaded(model.provider.id, model.modelId)
        val traits = info.traits
        val options = CompletionOptions(
            reasoningEffort = model.reasoningEffort?.takeIf { traits.reasoning },
            reasoningControl = traits.reasoningControl,
            output = traits.output,
            explicitCaching = info.family == ModelFamily.CLAUDE,
        )
        val toolSpecs = toolSpecsFor(info, toolSettings)
        val message = resumed?.let { chats.reopen(it, model.modelId) } ?: chats.startAssistantMessage(chatId, model.modelId)
        val written = resumed?.content.orEmpty()
        val turns = buildTurns(agent, toolSettings, chats.history(chatId).filterNot { it.id == message.id }, continuing = written)
        val prompt = promptFingerprint(turns, toolSpecs)
        val accumulator = ToolCallAccumulator()
        var draft = StreamingDraft(message.id, content = written, reasoning = "", toolCalls = emptyList(), runningCallIds = emptySet(), awaitingCallId = null, usage = null)
        publish(chatId, draft)
        try {
            streamResilient(model, turns, toolSpecs, options).collect { event ->
                draft = when (event) {
                    is CompletionEvent.ContentDelta -> draft.copy(content = draft.content + event.text)
                    is CompletionEvent.ReasoningDelta -> draft.copy(reasoning = draft.reasoning + event.text)
                    is CompletionEvent.ReasoningBlockDone -> draft.copy(reasoningBlocks = draft.reasoningBlocks + event.block)
                    is CompletionEvent.ImageOutput -> draft.copy(images = draft.images + event.url)
                    is CompletionEvent.ToolCallDelta -> draft.copy(toolCalls = accumulator.apply { add(event) }.calls())
                    is CompletionEvent.Finished -> draft
                    is CompletionEvent.Usage -> draft.copy(usage = event.usage)
                }
                publish(chatId, draft)
            }
            persist(message, draft, MessageStatus.COMPLETE, errorText = null, trace = reasoningTrace(model, prompt, draft))
            return RoundOutcome.Finished(draft)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { persist(message, draft, MessageStatus.CANCELLED, errorText = null, trace = reasoningTrace(model, prompt, draft)) }
            throw e
        } catch (e: Exception) {
            val error = ReplyErrors.encode(e)
            return RoundOutcome.Failed(draft, persist(message, draft, MessageStatus.ERROR, errorText = error, trace = null), e, error)
        }
    }

    suspend fun previewPrompt(chatId: String): PromptPreview? {
        val current = settings.current()
        val model = resolveModel(chatId, current) ?: return null
        val agent = current.agents.forChat(chats.chat(chatId)?.agentId)
        val toolSettings = agent.tools(current.tools)
        tools.awaitReady()
        val info = catalog.infoLoaded(model.provider.id, model.modelId)
        return PromptPreview(buildTurns(agent, toolSettings, chats.history(chatId), continuing = ""), toolSpecsFor(info, toolSettings))
    }

    private fun toolSpecsFor(info: ModelInfo, toolSettings: ToolSettings): List<ToolSpec> =
        if (info.traits.output == ModelOutput.IMAGE || info.toolUse == false) emptyList() else tools.specs(toolSettings)

    private suspend fun runTools(chatId: String, round: StreamingDraft, toolSettings: ToolSettings): Boolean {
        for (call in round.toolCalls) {
            val outcome = if (isApproved(chatId, round, call, toolSettings)) {
                publish(chatId, round.copy(runningCallIds = setOf(call.id)))
                if (gate.admit(call.name)) tools.run(call, toolSettings, ToolContext(chatId)) else ToolOutcome.error(LOCKED_REFUSAL)
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

    private suspend fun buildTurns(agent: AgentProfile, toolSettings: ToolSettings, history: List<MessageEntity>, continuing: String): List<ChatTurn> {
        val contents = mutableMapOf<String, AttachmentContent>()
        history.flatMap { Attachment.decode(it.attachmentsJson) }.forEach { attachment ->
            attachments.contentFor(attachment)?.let { contents[attachment.id] = it }
        }
        return TurnAssembler.assemble(systemPrompt.render(agent, tools.promptNotes(toolSettings), if (gate.privateContextAllowed()) memories.instant() else emptyList()), history, contents, continuing)
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

    private fun streamResilient(model: ResolvedModel, turns: List<ChatTurn>, toolSpecs: List<ToolSpec>, options: CompletionOptions): Flow<CompletionEvent> =
        streamWithReplayFallback(model, turns, toolSpecs, options).retryWhileSilent(onRetry = ::logRetry)

    private fun logRetry(attempt: Int, failure: ProviderException.Network, delayMs: Long) {
        Log.i(STREAM_LOG_TAG, "nothing received yet, retry $attempt in $delayMs ms after ${failure.kind}")
    }

    private fun streamWithReplayFallback(model: ResolvedModel, turns: List<ChatTurn>, toolSpecs: List<ToolSpec>, options: CompletionOptions): Flow<CompletionEvent> =
        completions.stream(model.provider, model.modelId, turns, toolSpecs, options).catch { failure ->
            if (failure !is ProviderException.Http || !failure.rejectsReplayedReasoning() || !turns.carriesReasoning()) throw failure
            emitAll(completions.stream(model.provider, model.modelId, turns.withoutReasoning(), toolSpecs, options))
        }

    private fun reasoningTrace(model: ResolvedModel, prompt: String, draft: StreamingDraft): ReasoningTrace? =
        draft.reasoningBlocks.takeIf { it.isNotEmpty() }?.let { ReasoningTrace(model.provider.format, model.modelId, prompt, it) }

    private suspend fun persist(message: MessageEntity, draft: StreamingDraft, status: MessageStatus, errorText: String?, trace: ReasoningTrace?): MessageEntity {
        val images = if (draft.images.isEmpty()) emptyList() else generatedImages.store(draft.images)
        val saved = message.copy(
            attachmentsJson = Attachment.encode(images),
            content = draft.content,
            reasoning = draft.reasoning.ifEmpty { null },
            reasoningTraceJson = trace?.let(TurnAssembler::encodeTrace),
            status = status,
            errorText = errorText,
            toolCallsJson = TurnAssembler.encodeToolCalls(draft.toolCalls),
            finishedAt = clock(),
            promptTokens = draft.usage?.promptTokens,
            completionTokens = draft.usage?.completionTokens,
            cachedTokens = draft.usage?.cachedTokens?.takeIf { it > 0 },
        )
        chats.save(saved)
        return saved
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
        const val LOCKED_REFUSAL = "The phone is locked and the user did not confirm it, so this tool did not run. Do not retry it; tell the user to unlock the phone or confirm when asked, then try again."
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
    val reasoningBlocks: List<ReasoningBlock> = emptyList(),
    val retry: RetryWait? = null,
)

data class RetryWait(val attempt: Int, val limit: Int, val resumesAt: Long, val error: String)

private sealed interface RoundOutcome {
    class Finished(val draft: StreamingDraft) : RoundOutcome

    class Failed(val draft: StreamingDraft, val row: MessageEntity, val failure: Exception, val error: String) : RoundOutcome
}

data class PromptPreview(val turns: List<ChatTurn>, val tools: List<ToolSpec>)

data class ResolvedModel(val provider: ProviderProfile, val modelId: String, val reasoningEffort: ReasoningEffort?)

class NoModelConfiguredException : IllegalStateException("Add a provider and choose a model in settings first")
