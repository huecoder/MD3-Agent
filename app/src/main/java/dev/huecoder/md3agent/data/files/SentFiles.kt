package dev.huecoder.md3agent.data.files

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.db.MessageDao
import dev.huecoder.md3agent.data.db.MessageRole

data class SentFile(val id: String, val file: ViewableFile, val sentByUser: Boolean, val sentAt: Long)

fun interface SentFiles {
    suspend fun all(): List<SentFile>
}

class ChatSentFiles(private val messages: MessageDao, private val attachments: AttachmentStore) : SentFiles {
    override suspend fun all(): List<SentFile> = messages.attachedFiles().flatMap { row ->
        Attachment.decode(row.attachmentsJson).map { SentFile(it.id, attachments.viewable(it), row.role == MessageRole.USER, row.createdAt) }
    }
}
