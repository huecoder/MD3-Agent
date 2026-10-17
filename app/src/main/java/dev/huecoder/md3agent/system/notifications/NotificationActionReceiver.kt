package dev.huecoder.md3agent.system.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(AppNotifications.EXTRA_CHAT_ID) ?: return
        when (intent.action) {
            AppNotifications.ACTION_APPROVAL -> answerApproval(context, chatId, intent)
        }
    }

    private fun answerApproval(context: Context, chatId: String, intent: Intent) {
        val callId = intent.getStringExtra(AppNotifications.EXTRA_CALL_ID) ?: return
        val decision = ApprovalDecision.entries.firstOrNull { it.name == intent.getStringExtra(AppNotifications.EXTRA_DECISION) } ?: return
        val container = (context.applicationContext as App).container
        val delivered = container.approvals.respond(chatId, callId, decision)
        Log.i(AppNotifications.TAG, "approval action decision=$decision delivered=$delivered")
        NotificationManagerCompat.from(context).cancel(chatId, NotificationIds.APPROVAL)
        if (!delivered) Toast.makeText(context, R.string.notification_request_expired, Toast.LENGTH_LONG).show()
    }
}
