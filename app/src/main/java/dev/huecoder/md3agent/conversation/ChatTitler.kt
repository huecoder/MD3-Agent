package dev.huecoder.md3agent.conversation

import android.util.Log
import dev.huecoder.md3agent.data.chat.ChatGlyph
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.chat.ChatShape
import dev.huecoder.md3agent.data.chat.ChatTint
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.db.TitleSource
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.llm.ChatCompletionClient
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.CompletionOptions
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.models.ModelOutput
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
