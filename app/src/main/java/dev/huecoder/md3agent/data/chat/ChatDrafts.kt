package dev.huecoder.md3agent.data.chat

import android.content.Context
import androidx.core.content.edit
import dev.huecoder.md3agent.data.attachments.Attachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatDraft(
    val text: String = "",
    val attachments: List<Attachment> = emptyList(),
    val savedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = text.isBlank() && attachments.isEmpty()
}

class ChatDrafts(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val drafts = MutableStateFlow(loadAll())

    val all: StateFlow<Map<String, ChatDraft>> = drafts.asStateFlow()

    fun read(chatId: String?): ChatDraft = drafts.value[keyOf(chatId)] ?: ChatDraft()

    fun write(chatId: String?, text: String, attachments: List<Attachment>) {
        val key = keyOf(chatId)
        val previous = drafts.value[key]
        if (previous?.text == text && previous.attachments == attachments) return
        val draft = ChatDraft(text, attachments, clock())
        when {
            !draft.isEmpty -> store(key, draft)
            previous != null -> forget(chatId)
        }
    }

    fun forget(chatId: String?) {
        val key = keyOf(chatId)
        drafts.update { it - key }
        prefs.edit { remove(key) }
    }

    private fun store(key: String, draft: ChatDraft) {
        drafts.update { it + (key to draft) }
        prefs.edit { putString(key, json.encodeToString(ChatDraft.serializer(), draft)) }
    }

    private fun loadAll(): Map<String, ChatDraft> =
        prefs.all.mapNotNull { (key, raw) -> (raw as? String)?.let { key to decode(it) } }.toMap().filterValues { !it.isEmpty }

    private fun decode(raw: String): ChatDraft =
        try {
            json.decodeFromString(ChatDraft.serializer(), raw)
        } catch (e: SerializationException) {
            ChatDraft(text = raw)
        }

    private fun keyOf(chatId: String?) = chatId ?: NEW_CHAT_KEY

    private companion object {
        const val PREFS_NAME = "chat_drafts"
        const val NEW_CHAT_KEY = "new"
        val json = Json { ignoreUnknownKeys = true }
    }
}
