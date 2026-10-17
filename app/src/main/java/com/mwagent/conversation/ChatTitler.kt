package com.mwagent.conversation

import android.util.Log
import com.mwagent.data.chat.ChatGlyph
import com.mwagent.data.chat.ChatLook
import com.mwagent.data.chat.ChatRepository
import com.mwagent.data.chat.ChatShape
import com.mwagent.data.chat.ChatTint
import com.mwagent.data.db.MessageRole
import com.mwagent.data.db.MessageStatus
import com.mwagent.data.db.TitleSource
import com.mwagent.data.models.ModelCatalog
import com.mwagent.llm.ChatCompletionClient
import com.mwagent.llm.ChatTurn
import com.mwagent.llm.CompletionEvent
import com.mwagent.llm.CompletionOptions
import com.mwagent.llm.ProviderException
import com.mwagent.llm.models.ModelOutput
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.fold
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ChatTitler(
    private val chats: ChatRepository,
    private val completions: ChatCompletionClient,
    private val catalog: ModelCatalog,
) {
    suspend fun labelIfNeeded(chatId: String, model: ResolvedModel) {
        val chat = chats.chat(chatId) ?: return
        if (chat.titleSource != TitleSource.FIRST_MESSAGE) return
        val history = chats.history(chatId)
        val question = history.firstOrNull { it.role == MessageRole.USER }?.content?.takeIf { it.isNotBlank() } ?: return
        val answer = history.firstOrNull { it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETE && it.content.isNotBlank() }?.content ?: return
        val traits = catalog.infoLoaded(model.provider.id, model.modelId).traits
        if (traits.output == ModelOutput.IMAGE) return
        val options = CompletionOptions(
            reasoningEffort = traits.reasoningEfforts.minOrNull().takeIf { traits.reasoning },
            reasoningControl = traits.reasoningControl,
        )
        val turns = listOf(
            ChatTurn(ChatTurn.Role.SYSTEM, INSTRUCTIONS),
            ChatTurn(ChatTurn.Role.USER, "User: ${question.take(EXCERPT_CHARS)}\n\nAssistant: ${answer.take(EXCERPT_CHARS)}"),
        )
        val reply = try {
            completions.stream(model.provider, model.modelId, turns, options = options)
                .filterIsInstance<CompletionEvent.ContentDelta>()
                .fold(StringBuilder()) { text, delta -> text.append(delta.text) }
                .toString()
        } catch (e: ProviderException) {
            Log.w(TAG, "Labeling failed", e)
            return
        }
        val label = ChatLabels.parse(reply) ?: return
        chats.applyGeneratedLabel(chatId, label.first, label.second)
    }

    private companion object {
        const val TAG = "ChatTitler"
        const val EXCERPT_CHARS = 1_200

        val INSTRUCTIONS = """
            You label conversations for a chat list. Read the exchange and answer with a single JSON object and nothing else:
            {"title": "...", "icon": "...", "color": "...", "shape": "..."}
            title: 2 to 5 words in the language of the conversation, sentence case, no quotes, no emoji, no final period.
            icon: the closest topic, one of ${ChatGlyph.entries.joinToString { it.key }}.
            color: fits the mood of the topic, one of ${ChatTint.entries.filter { it != ChatTint.THEME }.joinToString { it.key }}.
            shape: any of ${ChatShape.entries.joinToString { it.key }}, vary it so the list does not look uniform.
        """.trimIndent()
    }
}

object ChatLabels {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val TITLE_MAX_CHARS = 60
    private const val TITLE_DECORATIONS = "\"'«»“”."

    fun parse(reply: String): Pair<String, ChatLook>? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val raw = try {
            json.decodeFromString(RawLabel.serializer(), reply.substring(start, end + 1))
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        val title = raw.title.trim { it.isWhitespace() || it in TITLE_DECORATIONS }.take(TITLE_MAX_CHARS).trim()
        if (title.isEmpty()) return null
        val look = ChatLook(
            glyph = ChatGlyph.of(raw.icon?.lowercase()) ?: ChatGlyph.Default,
            tint = ChatTint.of(raw.color?.lowercase()) ?: ChatTint.entries.random(),
            shape = ChatShape.of(raw.shape?.lowercase()) ?: ChatShape.entries.random(),
        )
        return title to look
    }

    @Serializable
    private data class RawLabel(val title: String, val icon: String? = null, val color: String? = null, val shape: String? = null)
}
