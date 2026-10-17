package dev.huecoder.md3agent.system.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.conversation.PromptAnswer

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(AppNotifications.EXTRA_CHAT_ID) ?: return
        when (intent.action) {
            AppNotifications.ACTION_APPROVAL -> answerApproval(context, chatId, intent)
            AppNotifications.ACTION_ANSWER -> answerQuestion(context, chatId, intent)
        }
    }

    private fun answerQuestion(context: Context, chatId: String, intent: Intent) {
        val promptId = intent.getStringExtra(AppNotifications.EXTRA_PROMPT_ID) ?: return
        val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(AppNotifications.REPLY_KEY)?.toString()?.trim()
        val option = intent.getStringExtra(AppNotifications.EXTRA_ANSWER)
        val answer = when {
            !typed.isNullOrEmpty() -> PromptAnswer.Chosen(chosen = emptyList(), custom = typed)
            option != null -> PromptAnswer.Chosen(chosen = listOf(option), custom = null)
            else -> return
        }
        val container = (context.applicationContext as App).container
        val delivered = container.prompts.answer(promptId, answer)
        Log.i(AppNotifications.TAG, "question action typed=${!typed.isNullOrEmpty()} delivered=$delivered")
        NotificationManagerCompat.from(context).cancel(chatId, NotificationIds.APPROVAL)
        if (!delivered) Toast.makeText(context, R.string.notification_request_expired, Toast.LENGTH_LONG).show()
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
