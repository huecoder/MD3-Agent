package dev.huecoder.md3assistant.data.chat

import dev.huecoder.md3assistant.data.attachments.Attachment
import dev.huecoder.md3assistant.data.attachments.AttachmentStore
import dev.huecoder.md3assistant.data.db.ChatDao
import dev.huecoder.md3assistant.data.db.ChatEntity
import dev.huecoder.md3assistant.data.db.ChatSummary
import dev.huecoder.md3assistant.data.db.MessageDao
import dev.huecoder.md3assistant.data.db.MessageEntity
import dev.huecoder.md3assistant.data.db.MessageRole
import dev.huecoder.md3assistant.data.db.MessageStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val attachments: AttachmentStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSummaries(): Flow<List<ChatSummary>> = chatDao.observeSummaries()

    fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observe(chatId)

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.observeForChat(chatId)

    suspend fun chat(chatId: String): ChatEntity? = chatDao.get(chatId)

    suspend fun history(chatId: String): List<MessageEntity> = messageDao.listForChat(chatId)

    suspend fun createChat(title: String, providerId: String?, modelId: String?): ChatEntity {
        val now = clock()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            providerId = providerId,
            modelId = modelId,
        )
        chatDao.insert(chat)
        return chat
    }

    suspend fun appendUserMessage(chatId: String, text: String, attached: List<Attachment>): MessageEntity =
        newMessage(chatId, MessageRole.USER, text, MessageStatus.COMPLETE, modelId = null)
            .copy(attachmentsJson = Attachment.encode(attached))
            .also { save(it) }

    suspend fun appendToolResult(chatId: String, toolCallId: String, content: String, status: MessageStatus): MessageEntity =
        newMessage(chatId, MessageRole.TOOL, content, status, modelId = null)
            .copy(toolCallId = toolCallId)
            .also { save(it) }

    suspend fun startAssistantMessage(chatId: String, modelId: String): MessageEntity =
        newMessage(chatId, MessageRole.ASSISTANT, content = "", MessageStatus.STREAMING, modelId)
            .also { save(it) }

    suspend fun save(message: MessageEntity) {
        messageDao.upsert(message)
        chatDao.touch(message.chatId, clock())
    }

    suspend fun truncateAfter(message: MessageEntity) = messageDao.deleteFrom(message.chatId, message.createdAt + 1)

    suspend fun rename(chatId: String, title: String) = chatDao.rename(chatId, title)

    suspend fun setModel(chatId: String, providerId: String?, modelId: String?) =
        chatDao.setModel(chatId, providerId, modelId)

    suspend fun delete(chatId: String) {
        val attached = messageDao.listForChat(chatId).flatMap { Attachment.decode(it.attachmentsJson) }
        chatDao.delete(chatId)
        attachments.delete(attached)
    }

    suspend fun recoverInterruptedStreams() = messageDao.markInterruptedStreams()

    private fun newMessage(
        chatId: String,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        modelId: String?,
    ) = MessageEntity(
        id = UUID.randomUUID().toString(),
        chatId = chatId,
        role = role,
        content = content,
        reasoning = null,
        status = status,
        errorText = null,
        createdAt = monotonicNow(),
        modelId = modelId,
        toolCallsJson = null,
        toolCallId = null,
    )

    private var lastTimestamp = 0L

    @Synchronized
    private fun monotonicNow(): Long {
        lastTimestamp = maxOf(clock(), lastTimestamp + 1)
        return lastTimestamp
    }
}
