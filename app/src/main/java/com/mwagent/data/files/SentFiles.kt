package com.mwagent.data.files

import com.mwagent.data.attachments.Attachment
import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.db.MessageDao
import com.mwagent.data.db.MessageRole

data class SentFile(val id: String, val file: ViewableFile, val sentByUser: Boolean, val sentAt: Long)

fun interface SentFiles {
    suspend fun all(): List<SentFile>
}

class ChatSentFiles(private val messages: MessageDao, private val attachments: AttachmentStore) : SentFiles {
    override suspend fun all(): List<SentFile> = messages.attachedFiles().flatMap { row ->
        Attachment.decode(row.attachmentsJson).map { SentFile(it.id, attachments.viewable(it), row.role == MessageRole.USER, row.createdAt) }
    }
}
