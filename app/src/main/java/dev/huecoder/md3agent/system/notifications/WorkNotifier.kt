package dev.huecoder.md3agent.system.notifications

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ChatActivity
import dev.huecoder.md3agent.conversation.PendingPrompt
import dev.huecoder.md3agent.conversation.UserQuestion
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.settings.ApprovalSurface
import dev.huecoder.md3agent.data.settings.NotificationSettings
import dev.huecoder.md3agent.feature.chat.ToolStatus
import dev.huecoder.md3agent.feature.chat.title
import dev.huecoder.md3agent.feature.chat.toUi
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.ui.text.resolve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkNotifier(
    private val context: Context,
    private val container: AppContainer,
    private val onIdle: () -> Unit,
) {
    private val factory = NotificationFactory(context)
    private val progress = WorkProgress(SystemClock::elapsedRealtime)
    private val mutex = Mutex()
    private val titles = mutableMapOf<String, String>()
    private var scope: CoroutineScope? = null
    private var activity = emptyMap<String, ChatActivity>()
    private var appVisible = false
    private var settings = NotificationSettings()
    private var inputPosted = emptyMap<String, String>()
    private var workSignature: String? = null
    private var promotedReported: Boolean? = null
    private var ticks = 0
    private var finishing: Job? = null

    fun initial(): Notification = factory.simpleWork(count = 1)

    fun run(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            container.settings.settings.map { it.notifications }.distinctUntilChanged().collect {
                settings = it
                render()
            }
        }
        scope.launch {
            container.conversations.activity.combine(container.visibility.isVisible) { chats, visible -> chats to visible }.collect { (chats, visible) ->
                activity = chats
                appVisible = visible
                render()
            }
        }
        scope.launch {
            while (true) {
                delay(TICK_MS)
                render()
            }
        }
    }

    fun release() {
        val manager = manager()
        inputPosted.keys.forEach { manager?.cancel(it, NotificationIds.APPROVAL) }
    }

    private suspend fun render() = mutex.withLock {
        if (activity.isEmpty()) return@withLock finishWork()
        finishing?.cancel()
        finishing = null
        val views = progress.sync(container.conversations.drafts.value, activity.keys)
        postInput()
        postWork(views)
    }

    private suspend fun finishWork() {
        if (finishing != null) return
        val ended = progress.finished()
        progress.clear()
        val manager = manager()
        inputPosted.keys.forEach { manager?.cancel(it, NotificationIds.APPROVAL) }
        inputPosted = emptyMap()
        val outcomes = if (appVisible || !settings.taskDone) emptyMap() else ended.associateWith { outcomeOf(it) }
        outcomes.filterValues { it is Outcome.Failed }.forEach { (chatId, outcome) -> postResult(manager, chatId, outcome) }
        val done = outcomes.filterValues { it is Outcome.Done }
        val holdLive = done.isNotEmpty() && settings.liveStatus && manager != null && liveAvailable(manager)
        if (!holdLive) {
            done.forEach { (chatId, outcome) -> postResult(manager, chatId, outcome) }
            return onIdle()
        }
        manager?.notify(NotificationIds.WORK, factory.live(finalContent(done.keys.first()), settings.lockScreen))
        finishing = scope?.launch {
            delay(DONE_HOLD_MS)
            done.forEach { (chatId, outcome) -> postResult(manager, chatId, outcome) }
            onIdle()
        }
    }

    private suspend fun postResult(manager: NotificationManager?, chatId: String, outcome: Outcome) {
        val succeeded = outcome is Outcome.Done
        val preview = (outcome as? Outcome.Done)?.preview.orEmpty()
        manager?.notify(chatId, NotificationIds.RESULT, factory.result(chatId, titleOf(chatId), preview, succeeded, settings.lockScreen))
    }

    private suspend fun finalContent(chatId: String): LiveContent {
        val chatTitle = titleOf(chatId).ifEmpty { context.getString(R.string.notification_work_text) }
        val preview = (outcomeOf(chatId) as? Outcome.Done)?.preview.orEmpty()
        return LiveContent(
            chatId = chatId,
            title = context.getString(R.string.notification_done_title),
            text = chatTitle,
            details = preview.ifEmpty { chatTitle },
            chip = context.getString(R.string.chip_done),
            publicTitle = context.getString(R.string.notification_done_title),
            accent = context.getColor(R.color.notification_accent_done),
            finished = true,
        )
    }

    private suspend fun outcomeOf(chatId: String): Outcome {
        val reply = container.chats.history(chatId).lastOrNull { it.role == MessageRole.ASSISTANT } ?: return Outcome.Stopped
        return when (reply.status) {
            MessageStatus.COMPLETE -> Outcome.Done(reply.content.replace(WHITESPACE, " ").trim().take(PREVIEW_CHARS))
            MessageStatus.ERROR -> Outcome.Failed
            MessageStatus.CANCELLED, MessageStatus.STREAMING -> Outcome.Stopped
        }
    }

    private suspend fun postWork(views: Map<String, ProgressView>) {
        val manager = manager() ?: return
        val live = settings.liveStatus && liveAvailable(manager)
        val (signature, notification) = if (live) liveNotification(views) else activity.size.let { count -> "simple$count" to factory.simpleWork(count) }
        if (signature != workSignature) {
            manager.notify(NotificationIds.WORK, notification)
            workSignature = signature
        }
        if (live && ++ticks % PROMOTION_CHECK_EVERY == 0) reportPromotion(manager)
    }

    private suspend fun liveNotification(views: Map<String, ProgressView>): Pair<String, Notification> {
        val drafts = container.conversations.drafts.value
        val asking = activity.filterValues { it == ChatActivity.NEEDS_INPUT }.keys
        val chatId = asking.firstOrNull() ?: views.keys.first()
        val view = views.getValue(chatId)
        val approval = drafts[chatId]?.awaitingCallId != null
        val asked = chatId in asking
        val title = when {
            asked -> context.getString(if (approval) R.string.chat_tool_status_awaiting else R.string.notification_live_waiting_answer)
            view.currentCall != null -> view.currentCall.toUi(ToolStatus.RUNNING).title().resolve(context)
            view.isWriting -> context.getString(R.string.notification_live_writing)
            else -> context.getString(R.string.chat_thinking)
        }
        val chatTitle = titleOf(chatId).ifEmpty { context.getString(R.string.notification_work_text) }
        val others = activity.size - 1
        val lines = view.earlierCalls.map { "• ${it.toUi(ToolStatus.DONE).title().resolve(context)}" } +
            listOfNotNull(view.thought?.let { context.getString(R.string.notification_live_thought, it) })
        val content = LiveContent(
            chatId = chatId,
            title = title,
            text = if (others > 0) "$chatTitle · +$others" else chatTitle,
            details = lines.joinToString("\n").ifEmpty { chatTitle },
            chip = if (asked) context.getString(R.string.chip_ask) else ChipText.working(settings.chip, view.elapsedMs, view.tokens, ChipUnits.of(context.resources)),
            publicTitle = context.getString(if (asked) R.string.notification_approval_title else R.string.notification_live_public),
            accent = context.getColor(if (asked) R.color.notification_accent_ask else R.color.notification_accent),
        )
        val signature = listOf(content, settings.lockScreen).joinToString("\u0001")
        return signature to factory.live(content, settings.lockScreen)
    }

    private suspend fun postInput() {
        val manager = manager()
        val needed = if (appVisible) emptyMap() else inputNeeded()
        (inputPosted.keys - needed.keys).forEach { manager?.cancel(it, NotificationIds.APPROVAL) }
        needed.filter { (chatId, need) -> inputPosted[chatId] != need.key }.forEach { (chatId, need) -> manager?.let { post(it, chatId, need) } }
        inputPosted = needed.mapValues { it.value.key }
    }

    private fun inputNeeded(): Map<String, InputNeed> {
        val drafts = container.conversations.drafts.value
        val prompts = container.prompts.pending.value
        return activity.filterValues { it == ChatActivity.NEEDS_INPUT }.mapValues { (chatId, _) ->
            val draft = drafts[chatId]
            val call = draft?.toolCalls?.firstOrNull { it.id == draft.awaitingCallId }
            val prompt = prompts[chatId]
            when {
                call != null -> InputNeed.Approval(call)
                prompt != null && prompt.prompt is UserQuestion -> InputNeed.Question(prompt, prompt.prompt)
                else -> InputNeed.Form
            }
        }
    }

    private suspend fun post(manager: NotificationManager, chatId: String, need: InputNeed) {
        val title = titleOf(chatId)
        val notification = when (need) {
            is InputNeed.Approval -> factory.approval(ApprovalNotice(chatId, title, need.call), withButtons = !overlayWillAsk(), settings.lockScreen)
            is InputNeed.Question -> factory.question(QuestionNotice(chatId, title, need.pending.id, need.question), settings.lockScreen)
            InputNeed.Form -> factory.waiting(chatId, title, settings.lockScreen)
        }
        manager.notify(chatId, NotificationIds.APPROVAL, notification)
    }

    private fun overlayWillAsk(): Boolean {
        if (settings.approvals != ApprovalSurface.OVERLAY) return false
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        return container.phoneControl.isReady() && !locked
    }

    private suspend fun titleOf(chatId: String): String =
        titles[chatId] ?: container.chats.chat(chatId)?.title.orEmpty().also { titles[chatId] = it }

    private fun liveAvailable(manager: NotificationManager): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && manager.canPostPromotedNotifications()

    private fun reportPromotion(manager: NotificationManager) {
        val posted = manager.activeNotifications.firstOrNull { it.id == NotificationIds.WORK }?.notification ?: return
        val promoted = posted.flags and Notification.FLAG_PROMOTED_ONGOING != 0
        if (promoted == promotedReported) return
        promotedReported = promoted
        Log.i(AppNotifications.TAG, "live update promoted=$promoted promotable=${NotificationCompat.hasPromotableCharacteristics(posted)} canPost=${manager.canPostPromotedNotifications()}")
    }

    private fun manager(): NotificationManager? =
        if (AppNotifications.canPost(context)) context.getSystemService(NotificationManager::class.java) else null

    private sealed interface InputNeed {
        val key: String

        data class Approval(val call: ToolCall) : InputNeed {
            override val key: String get() = call.id
        }

        data class Question(val pending: PendingPrompt, val question: UserQuestion) : InputNeed {
            override val key: String get() = pending.id
        }

        data object Form : InputNeed {
            override val key: String = "form"
        }
    }

    private sealed interface Outcome {
        data class Done(val preview: String) : Outcome

        data object Failed : Outcome

        data object Stopped : Outcome
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val DONE_HOLD_MS = 4_000L
        const val PROMOTION_CHECK_EVERY = 5
        const val PREVIEW_CHARS = 240
        val WHITESPACE = Regex("""\s+""")
    }
}
