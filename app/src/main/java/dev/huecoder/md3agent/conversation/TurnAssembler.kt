package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentContent
import dev.huecoder.md3agent.data.attachments.ByteSize
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.ToolCall
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object TurnAssembler {
    private val json = Json { ignoreUnknownKeys = true }
    private val toolCallList = ListSerializer(ToolCall.serializer())

    fun decodeToolCalls(raw: String?): List<ToolCall> =
        raw?.let { runCatching { json.decodeFromString(toolCallList, it) }.getOrNull() }.orEmpty()

    fun encodeToolCalls(calls: List<ToolCall>): String? = calls.takeIf { it.isNotEmpty() }?.let { json.encodeToString(toolCallList, it) }

    fun assemble(
        systemPrompt: String,
        history: List<MessageEntity>,
        attachments: Map<String, AttachmentContent>,
    ): List<ChatTurn> {
        val answeredCallIds = history.filter { it.role == MessageRole.TOOL }.mapNotNullTo(HashSet()) { it.toolCallId }
        val keptCallIds = HashSet<String>()
        val turns = mutableListOf(ChatTurn(ChatTurn.Role.SYSTEM, systemPrompt))
        val toolImages = mutableListOf<String>()

        fun flushToolImages() {
            if (toolImages.isEmpty()) return
            turns += ChatTurn(ChatTurn.Role.USER, TOOL_IMAGES_NOTE, imageUrls = toolImages.toList())
            toolImages.clear()
        }

        for (message in history) {
            if (message.role != MessageRole.TOOL) flushToolImages()
            when (message.role) {
                MessageRole.USER -> if (message.status != MessageStatus.ERROR) {
                    turns += userTurn(message, Attachment.decode(message.attachmentsJson).mapNotNull { attachments[it.id] })
                }
                MessageRole.ASSISTANT -> {
                    val calls = decodeToolCalls(message.toolCallsJson).filter { it.id in answeredCallIds }
                    keptCallIds += calls.map { it.id }
                    val text = if (message.status == MessageStatus.ERROR) "" else message.content
                    if (text.isNotBlank() || calls.isNotEmpty()) turns += ChatTurn(ChatTurn.Role.ASSISTANT, text, toolCalls = calls)
                }
                MessageRole.TOOL -> if (message.toolCallId in keptCallIds) {
                    turns += ChatTurn(ChatTurn.Role.TOOL, message.content, toolCallId = message.toolCallId)
                    toolImages += Attachment.decode(message.attachmentsJson)
                        .mapNotNull { attachments[it.id] as? AttachmentContent.Image }
                        .map { it.dataUrl }
                }
                MessageRole.SYSTEM -> Unit
            }
        }
        flushToolImages()
        return markCacheBreakpoints(turns)
    }

    private fun markCacheBreakpoints(turns: List<ChatTurn>): List<ChatTurn> = turns.mapIndexed { index, turn ->
        if (index == 0 || index == turns.lastIndex) turn.copy(cacheBreakpoint = true) else turn
    }

    private fun userTurn(message: MessageEntity, contents: List<AttachmentContent>): ChatTurn {
        val text = buildString {
            append(message.content)
            contents.forEach { content ->
                when (content) {
                    is AttachmentContent.Text -> {
                        if (isNotEmpty()) append("\n\n")
                        append("File \"").append(content.name).append("\":\n```\n").append(content.text.trimEnd()).append("\n```")
                    }
                    is AttachmentContent.Stored -> {
                        if (isNotEmpty()) append("\n\n")
                        append("File \"").append(content.name).append("\" (").append(content.mimeType).append(", ")
                            .append(ByteSize.describe(content.sizeBytes)).append(") is saved on the device at ").append(content.path)
                            .append(". Use the file tools to work with it.")
                    }
                    is AttachmentContent.Image -> Unit
                }
            }
            message.contextNote?.let { note ->
                if (isNotEmpty()) append("\n\n")
                append(CONTEXT_OPEN).append(note).append(CONTEXT_CLOSE)
            }
        }
        return ChatTurn(
            role = ChatTurn.Role.USER,
            content = text,
            imageUrls = contents.filterIsInstance<AttachmentContent.Image>().map { it.dataUrl },
        )
    }

    private const val TOOL_IMAGES_NOTE = "Images returned by the tool calls above."
    private const val CONTEXT_OPEN = "<context>"
    private const val CONTEXT_CLOSE = "</context>"
}
