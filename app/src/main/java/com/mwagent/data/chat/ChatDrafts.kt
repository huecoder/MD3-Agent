package com.mwagent.data.chat

import android.content.Context
import androidx.core.content.edit

class ChatDrafts(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(chatId: String?): String = prefs.getString(keyOf(chatId), null).orEmpty()

    fun write(chatId: String?, text: String) = prefs.edit {
        if (text.isBlank()) remove(keyOf(chatId)) else putString(keyOf(chatId), text)
    }

    fun forget(chatId: String) = prefs.edit { remove(keyOf(chatId)) }

    private fun keyOf(chatId: String?) = chatId ?: NEW_CHAT_KEY

    private companion object {
        const val PREFS_NAME = "chat_drafts"
        const val NEW_CHAT_KEY = "new"
    }
}
