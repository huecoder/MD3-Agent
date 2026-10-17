package dev.huecoder.md3agent.system.notifications

import android.app.Notification
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.conversation.ApprovalRisk
import dev.huecoder.md3agent.conversation.UserQuestion
import dev.huecoder.md3agent.data.settings.LockScreenDetail
import dev.huecoder.md3agent.feature.chat.ToolStatus
import dev.huecoder.md3agent.feature.chat.toUi
import dev.huecoder.md3agent.feature.chat.readableArguments
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.ui.components.ToolCatalog
import dev.huecoder.md3agent.ui.text.resolve

data class ApprovalNotice(val chatId: String, val chatTitle: String, val call: ToolCall)

data class QuestionNotice(val chatId: String, val chatTitle: String, val promptId: String, val question: UserQuestion)

data class LiveContent(
    val chatId: String?,
    val title: String,
    val text: String,
    val details: String,
    val chip: String,
    val publicTitle: String,
    val accent: Int,
    val finished: Boolean = false,
)

enum class PromotionRule { OPT_IN, COLORIZED, NONE }

class NotificationFactory(private val context: Context) {
    private var lastRule: PromotionRule? = null

    fun simpleWork(count: Int): Notification =
        NotificationCompat.Builder(context, AppChannel.BACKGROUND_WORK.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notification_work_title, count, count))
            .setContentText(context.getString(R.string.notification_work_text))
            .setContentIntent(AppNotifications.openChatIntent(context, chatId = null, NotificationIds.OPEN_APP_REQUEST))
            .addAction(0, context.getString(R.string.notification_work_stop), AppNotifications.stopAllIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun live(content: LiveContent, lockScreen: LockScreenDetail): Notification {
        val optIn = liveBuilder(content, lockScreen).setRequestPromotedOngoing(true).build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return optIn
        if (NotificationCompat.hasPromotableCharacteristics(optIn)) return optIn.also { note(PromotionRule.OPT_IN) }
        val colorized = liveBuilder(content, lockScreen).setColorized(true).build()
        return if (NotificationCompat.hasPromotableCharacteristics(colorized)) colorized.also { note(PromotionRule.COLORIZED) } else optIn.also { note(PromotionRule.NONE) }
    }

    fun approval(notice: ApprovalNotice, withButtons: Boolean, lockScreen: LockScreenDetail): Notification {
        val toolTitle = ToolCatalog.of(notice.call.name).title.resolve(context)
        val details = readableArguments(notice.call.toUi(ToolStatus.AWAITING_APPROVAL).arguments).take(DETAILS_MAX_CHARS)
        val builder = NotificationCompat.Builder(context, AppChannel.APPROVALS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.approval_title, toolTitle))
            .setContentText(details.lineSequence().firstOrNull().orEmpty().ifEmpty { notice.chatTitle })
            .setStyle(NotificationCompat.BigTextStyle().bigText(details.ifEmpty { notice.chatTitle }).setSummaryText(notice.chatTitle))
            .setContentIntent(AppNotifications.openChatIntent(context, notice.chatId, notice.chatId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .withLockScreen(lockScreen, context.getString(R.string.notification_approval_title))
        if (withButtons) {
            val needsUnlock = ApprovalRisk.requiresUnlock(notice.call.name)
            builder.addAction(approvalAction(notice, ApprovalDecision.ALLOW, R.string.approval_allow, needsUnlock))
            builder.addAction(approvalAction(notice, ApprovalDecision.DENY, R.string.approval_deny, authenticate = false))
            builder.addAction(approvalAction(notice, ApprovalDecision.ALLOW_FOR_SESSION, R.string.notification_approval_always, needsUnlock))
        }
        return builder.build()
    }

    fun question(notice: QuestionNotice, lockScreen: LockScreenDetail): Notification {
        val question = notice.question
        val optionLines = question.options.mapIndexed { index, option -> "${index + 1}. ${option.label}${option.description?.let { " — $it" }.orEmpty()}" }
        val buttons = if (question.multiSelect) emptyList() else question.options.take(directButtons(question))
        val builder = NotificationCompat.Builder(context, AppChannel.APPROVALS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(question.question)
            .setContentText(question.options.joinToString(" · ") { it.label })
            .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(question.question) + optionLines).joinToString("\n")).setSummaryText(notice.chatTitle))
            .setContentIntent(AppNotifications.openChatIntent(context, notice.chatId, notice.chatId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .withLockScreen(lockScreen, context.getString(R.string.notification_approval_title))
        buttons.forEachIndexed { slot, option -> builder.addAction(answerAction(notice, option.label, slot)) }
        if (question.allowCustom || question.options.size > buttons.size) builder.addAction(typedAction(notice))
        return builder.build()
    }

    fun result(chatId: String, chatTitle: String, preview: String, succeeded: Boolean, lockScreen: LockScreenDetail): Notification {
        val title = context.getString(if (succeeded) R.string.notification_done_title else R.string.notification_failed_title)
        return NotificationCompat.Builder(context, AppChannel.RESULTS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(if (succeeded) R.color.notification_accent_done else R.color.notification_accent_failed))
            .setContentTitle(title)
            .setContentText(chatTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview.ifEmpty { chatTitle }).setSummaryText(chatTitle))
            .setContentIntent(AppNotifications.openChatIntent(context, chatId, chatId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .withLockScreen(lockScreen, title)
            .build()
    }

    fun waiting(chatId: String, chatTitle: String, lockScreen: LockScreenDetail): Notification =
        NotificationCompat.Builder(context, AppChannel.APPROVALS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_approval_title))
            .setContentText(chatTitle)
            .setContentIntent(AppNotifications.openChatIntent(context, chatId, chatId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .withLockScreen(lockScreen, context.getString(R.string.notification_approval_title))
            .build()

    private fun liveBuilder(content: LiveContent, lockScreen: LockScreenDetail): NotificationCompat.Builder =
        NotificationCompat.Builder(context, AppChannel.BACKGROUND_WORK.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(content.accent)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.details))
            .setShortCriticalText(content.chip)
            .setContentIntent(AppNotifications.openChatIntent(context, content.chatId, NotificationIds.OPEN_APP_REQUEST))
            .apply {
                if (content.finished) return@apply
                setProgress(0, 0, true)
                addAction(0, context.getString(R.string.notification_work_stop), AppNotifications.stopAllIntent(context))
            }
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .withLockScreen(lockScreen, content.publicTitle, content.chip)

    private fun NotificationCompat.Builder.withLockScreen(lockScreen: LockScreenDetail, publicTitle: String, chip: String? = null): NotificationCompat.Builder =
        when (lockScreen) {
            LockScreenDetail.EVERYTHING -> setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            LockScreenDetail.STATUS_ONLY -> setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion(publicTitle, chip))
            LockScreenDetail.NOTHING -> setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }

    private fun publicVersion(title: String, chip: String?): Notification =
        NotificationCompat.Builder(context, AppChannel.APPROVALS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .apply { chip?.let { setShortCriticalText(it) } }
            .build()

    private fun directButtons(question: UserQuestion): Int =
        if (question.allowCustom || question.options.size > MAX_ACTIONS) MAX_ACTIONS - 1 else MAX_ACTIONS

    private fun answerAction(notice: QuestionNotice, option: String, slot: Int): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, option, AppNotifications.answerIntent(context, notice.chatId, notice.promptId, option, slot)).build()

    private fun typedAction(notice: QuestionNotice): NotificationCompat.Action {
        val input = RemoteInput.Builder(AppNotifications.REPLY_KEY).setLabel(context.getString(R.string.notification_question_reply)).build()
        return NotificationCompat.Action.Builder(0, context.getString(R.string.notification_question_other), AppNotifications.answerIntent(context, notice.chatId, notice.promptId, option = null, slot = MAX_ACTIONS))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun approvalAction(notice: ApprovalNotice, decision: ApprovalDecision, label: Int, authenticate: Boolean): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, context.getString(label), AppNotifications.approvalIntent(context, notice.chatId, notice.call.id, decision))
            .setAuthenticationRequired(authenticate)
            .build()

    private fun note(rule: PromotionRule) {
        if (rule == lastRule) return
        lastRule = rule
        Log.i(AppNotifications.TAG, "live update rule=$rule")
    }

    private companion object {
        const val DETAILS_MAX_CHARS = 400
        const val MAX_ACTIONS = 3
    }
}
