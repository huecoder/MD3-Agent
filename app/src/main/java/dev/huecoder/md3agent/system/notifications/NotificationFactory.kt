package dev.huecoder.md3agent.system.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.conversation.ApprovalRisk
import dev.huecoder.md3agent.feature.chat.awaitingApprovalUi
import dev.huecoder.md3agent.feature.chat.readableArguments
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.ui.components.ToolCatalog
import dev.huecoder.md3agent.ui.text.resolve

data class ApprovalNotice(val chatId: String, val chatTitle: String, val call: ToolCall)

class NotificationFactory(private val context: Context) {
    fun approval(notice: ApprovalNotice, withButtons: Boolean): Notification {
        val toolTitle = ToolCatalog.of(notice.call.name).title.resolve(context)
        val details = readableArguments(notice.call.awaitingApprovalUi().arguments).take(DETAILS_MAX_CHARS)
        val builder = NotificationCompat.Builder(context, AppChannel.APPROVALS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.approval_title, toolTitle))
            .setContentText(details.lineSequence().firstOrNull().orEmpty().ifEmpty { notice.chatTitle })
            .setStyle(NotificationCompat.BigTextStyle().bigText(details.ifEmpty { notice.chatTitle }).setSummaryText(notice.chatTitle))
            .setContentIntent(AppNotifications.openChatIntent(context, notice.chatId, notice.chatId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(waitingPublicVersion())
        if (withButtons) {
            val needsUnlock = ApprovalRisk.requiresUnlock(notice.call.name)
            builder.addAction(approvalAction(notice, ApprovalDecision.ALLOW, R.string.approval_allow, needsUnlock))
            builder.addAction(approvalAction(notice, ApprovalDecision.DENY, R.string.approval_deny, authenticate = false))
            builder.addAction(approvalAction(notice, ApprovalDecision.ALLOW_FOR_SESSION, R.string.notification_approval_always, needsUnlock))
        }
        return builder.build()
    }

    fun waiting(chatId: String, chatTitle: String): Notification =
        NotificationCompat.Builder(context, AppChannel.APPROVALS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_approval_title))
            .setContentText(chatTitle)
            .setContentIntent(AppNotifications.openChatIntent(context, chatId, chatId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

    private fun waitingPublicVersion(): Notification =
        NotificationCompat.Builder(context, AppChannel.APPROVALS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_approval_title))
            .build()

    private fun approvalAction(notice: ApprovalNotice, decision: ApprovalDecision, label: Int, authenticate: Boolean): NotificationCompat.Action {
        val intent: PendingIntent = AppNotifications.approvalIntent(context, notice.chatId, notice.call.id, decision)
        return NotificationCompat.Action.Builder(0, context.getString(label), intent)
            .setAuthenticationRequired(authenticate)
            .build()
    }

    private companion object {
        const val DETAILS_MAX_CHARS = 400
    }
}
