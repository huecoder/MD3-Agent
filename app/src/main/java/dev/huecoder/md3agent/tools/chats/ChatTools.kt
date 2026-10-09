package dev.huecoder.md3agent.tools.chats

import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.chat.ChatTranscript
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.files.DeviceFiles
import dev.huecoder.md3agent.tools.ContextualTool
import dev.huecoder.md3agent.tools.ToolArgumentException
import dev.huecoder.md3agent.tools.ToolContext
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.optionalString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListChatsTool(private val chats: ChatRepository) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Lists the user's other chats in this app, newest first, with id, title, last activity and a preview of the last message. " +
            "Use it when the user refers to an earlier conversation. Pass a query to search titles and message text.",
        parameters = objectSchema(required = emptyList()) {
            stringProperty("query", "Optional words to search for in titles and messages")
            integerProperty("limit", "How many chats to return, default $DEFAULT_LIMIT", minimum = 1, maximum = MAX_LIMIT)
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val query = arguments.optionalString("query")
        val limit = (arguments.optionalInt("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val matching = query?.let { chats.searchChatIds(it) }
        val found = chats.observeSummaries().first().filter { it.trashedAt == null && (matching == null || it.id in matching) }.take(limit)
        if (found.isEmpty()) return ToolOutcome(if (query == null) "There are no chats yet" else "No chats match \"$query\"")
        val dates = SimpleDateFormat(DATE_PATTERN, Locale.ROOT)
        return ToolOutcome(
            found.joinToString("\n") { chat ->
                val marker = if (chat.id == context.chatId) " (this chat)" else ""
                val preview = chat.lastMessage?.replace(WHITESPACE, " ")?.take(PREVIEW_CHARS).orEmpty()
                "${chat.id} | ${chat.title}$marker | ${dates.format(Date(chat.updatedAt))} | $preview"
            },
        )
    }

    companion object {
        const val NAME = "list_chats"
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 50
        private const val PREVIEW_CHARS = 120
    }
}

class ReadChatTool(private val chats: ChatRepository) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads messages from another chat by its id from ${ListChatsTool.NAME}. Returns the latest messages, oldest first. " +
            "Use offset to page further back.",
        parameters = objectSchema(required = listOf("chat_id")) {
            stringProperty("chat_id", "Chat id from ${ListChatsTool.NAME}")
            integerProperty("limit", "How many messages to read, default $DEFAULT_LIMIT", minimum = 1, maximum = MAX_LIMIT)
            integerProperty("offset", "How many of the newest messages to skip, default 0", minimum = 0, maximum = MAX_OFFSET)
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val chat = chats.chat(requireChatId(arguments)) ?: return ToolOutcome.error("No chat with this id, call ${ListChatsTool.NAME} first")
        val limit = (arguments.optionalInt("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val offset = (arguments.optionalInt("offset") ?: 0).coerceIn(0, MAX_OFFSET)
        val spoken = ChatTranscript.spoken(chats.history(chat.id))
        val window = spoken.dropLast(offset).takeLast(limit)
        if (window.isEmpty()) return ToolOutcome("\"${chat.title}\" has no messages in this range (${spoken.size} in total)")
        val first = spoken.size - offset - window.size + 1
        val header = "\"${chat.title}\", messages $first-${first + window.size - 1} of ${spoken.size}:"
        return ToolOutcome(header + "\n\n" + ChatTranscript.excerpt(window, MAX_MESSAGE_CHARS))
    }

    companion object {
        const val NAME = "read_chat"
        private const val DEFAULT_LIMIT = 30
        private const val MAX_LIMIT = 100
        private const val MAX_OFFSET = 10_000
        private const val MAX_MESSAGE_CHARS = 4_000
    }
}

class ExportChatTool(private val chats: ChatRepository, private val files: DeviceFiles) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Saves a whole chat as a Markdown file on the phone. Without chat_id it saves the current chat. " +
            "Without path it goes to the app workspace under the chat's title.",
        parameters = objectSchema(required = emptyList()) {
            stringProperty("chat_id", "Chat id from ${ListChatsTool.NAME}, defaults to this chat")
            stringProperty("path", "Where to save, for example /sdcard/Download/notes.md")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val chatId = arguments.optionalString("chat_id") ?: context.chatId
        val chat = chats.chat(chatId) ?: return ToolOutcome.error("No chat with this id, call ${ListChatsTool.NAME} first")
        val target = files.resolve(arguments.optionalString("path") ?: "${fileNameOf(chat.title)}.md")
        val markdown = ChatTranscript.markdown(chat.title, chats.history(chat.id))
        files.write(target, settings.access.files, markdown.toByteArray(), append = false)
        return ToolOutcome("Saved \"${chat.title}\" to ${target.path}. Offer to show it with send_files if the user wants the file.")
    }

    private fun fileNameOf(title: String) = title.replace(UNSAFE_FILE_CHARS, "_").trim('_', ' ').take(MAX_FILE_NAME).ifEmpty { "chat" }

    companion object {
        const val NAME = "export_chat"
        private const val MAX_FILE_NAME = 64
        private val UNSAFE_FILE_CHARS = Regex("""[\\/:*?"<>|\s]+""")
    }
}

private fun requireChatId(arguments: JsonObject): String =
    arguments.optionalString("chat_id") ?: throw ToolArgumentException("Pass chat_id from ${ListChatsTool.NAME}")

private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
private val WHITESPACE = Regex("""\s+""")
