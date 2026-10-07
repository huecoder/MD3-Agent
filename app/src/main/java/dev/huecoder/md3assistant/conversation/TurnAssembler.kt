package dev.huecoder.md3assistant.conversation

import dev.huecoder.md3assistant.data.attachments.Attachment
import dev.huecoder.md3assistant.data.attachments.AttachmentContent
import dev.huecoder.md3assistant.data.db.MessageEntity
import dev.huecoder.md3assistant.data.db.MessageRole
import dev.huecoder.md3assistant.data.db.MessageStatus
import dev.huecoder.md3assistant.llm.ChatTurn
import dev.huecoder.md3assistant.llm.ToolCall
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

        for (message in history) {
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
                }
                MessageRole.SYSTEM -> Unit
            }
        }
        return turns
    }

    private fun userTurn(message: MessageEntity, contents: List<AttachmentContent>): ChatTurn {
        val files = contents.filterIsInstance<AttachmentContent.Text>()
        val text = buildString {
            append(message.content)
            files.forEach { file ->
                if (isNotEmpty()) append("\n\n")
                append("Файл «").append(file.name).append("»:\n```\n").append(file.text.trimEnd()).append("\n```")
            }
        }
        return ChatTurn(
            role = ChatTurn.Role.USER,
            content = text,
            imageUrls = contents.filterIsInstance<AttachmentContent.Image>().map { it.dataUrl },
        )
    }
}
