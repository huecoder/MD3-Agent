package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ChatTranscript {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun markdown(title: String, messages: List<MessageEntity>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("# ").append(title).append("\n\n")
        spoken(messages).forEach { message ->
            append("## ").append(speaker(message.role)).append(" · ").append(format(message.createdAt, zone)).append("\n\n")
            append(message.content.trim()).append("\n")
            attachmentNames(message).takeIf { it.isNotEmpty() }?.let { append("\n_Attachments: ").append(it.joinToString()).append("_\n") }
            append("\n")
        }
    }.trimEnd() + "\n"

    fun excerpt(messages: List<MessageEntity>, maxCharsPerMessage: Int, zone: ZoneId = ZoneId.systemDefault()): String =
        spoken(messages).joinToString("\n\n") { message ->
            val text = message.content.trim()
            val clipped = if (text.length > maxCharsPerMessage) text.take(maxCharsPerMessage) + " [...]" else text
            val files = attachmentNames(message).takeIf { it.isNotEmpty() }?.joinToString(prefix = " [files: ", postfix = "]").orEmpty()
            "[${format(message.createdAt, zone)}] ${speaker(message.role)}: $clipped$files"
        }

    fun spoken(messages: List<MessageEntity>): List<MessageEntity> =
        messages.filter { (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) && (it.content.isNotBlank() || it.attachmentsJson != null) }

    private fun attachmentNames(message: MessageEntity) = Attachment.decode(message.attachmentsJson).map { it.name }

    private fun speaker(role: MessageRole) = if (role == MessageRole.USER) "User" else "Assistant"

    private fun format(epochMillis: Long, zone: ZoneId) = stamp.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
