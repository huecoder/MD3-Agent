package dev.huecoder.md3agent.system.notifications

import android.app.Notification
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.conversation.ApprovalRisk
import dev.huecoder.md3agent.data.settings.LockScreenDetail
import dev.huecoder.md3agent.feature.chat.ToolStatus
import dev.huecoder.md3agent.feature.chat.toUi
import dev.huecoder.md3agent.feature.chat.readableArguments
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.ui.components.ToolCatalog
import dev.huecoder.md3agent.ui.text.resolve

data class ApprovalNotice(val chatId: String, val chatTitle: String, val call: ToolCall)

data class LiveContent(
    val chatId: String?,
    val title: String,
    val text: String,
    val details: String,
    val chip: String,
    val publicTitle: String,
    val accent: Int,
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
            .setProgress(0, 0, true)
            .setShortCriticalText(content.chip)
            .setContentIntent(AppNotifications.openChatIntent(context, content.chatId, NotificationIds.OPEN_APP_REQUEST))
            .addAction(0, context.getString(R.string.notification_work_stop), AppNotifications.stopAllIntent(context))
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
    }
}
