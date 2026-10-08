package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.db.ChatDao
import dev.huecoder.md3agent.data.db.ChatEntity
import dev.huecoder.md3agent.data.db.ChatSummary
import dev.huecoder.md3agent.data.db.FolderDao
import dev.huecoder.md3agent.data.db.FolderEntity
import dev.huecoder.md3agent.data.db.MessageDao
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.settings.ModelSelection
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val folderDao: FolderDao,
    private val attachments: AttachmentStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSummaries(): Flow<List<ChatSummary>> = chatDao.observeSummaries()

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    suspend fun searchChatIds(query: String): Set<String> =
        chatDao.search(query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")).toSet()

    fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observe(chatId)

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.observeForChat(chatId)

    suspend fun chat(chatId: String): ChatEntity? = chatDao.get(chatId)

    suspend fun history(chatId: String): List<MessageEntity> = messageDao.listForChat(chatId)

    suspend fun createChat(title: String, selection: ModelSelection): ChatEntity {
        val now = clock()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            providerId = selection.providerId,
            modelId = selection.modelId,
            reasoningEffort = selection.reasoningEffort?.wireName,
        )
        chatDao.insert(chat)
        return chat
    }

    suspend fun appendUserMessage(chatId: String, text: String, attached: List<Attachment>, contextNote: String? = null): MessageEntity =
        newMessage(chatId, MessageRole.USER, text, MessageStatus.COMPLETE, modelId = null)
            .copy(attachmentsJson = Attachment.encode(attached), contextNote = contextNote)
            .also { save(it) }

    suspend fun appendToolResult(chatId: String, toolCallId: String, content: String, status: MessageStatus, attached: List<Attachment>): MessageEntity =
        newMessage(chatId, MessageRole.TOOL, content, status, modelId = null)
            .copy(toolCallId = toolCallId, attachmentsJson = Attachment.encode(attached))
            .also { save(it) }

    suspend fun startAssistantMessage(chatId: String, modelId: String): MessageEntity =
        newMessage(chatId, MessageRole.ASSISTANT, content = "", MessageStatus.STREAMING, modelId)
            .also { save(it) }

    suspend fun save(message: MessageEntity) {
        messageDao.upsert(message)
        chatDao.touch(message.chatId, clock())
    }

    suspend fun truncateAfter(message: MessageEntity) {
        val from = message.createdAt + 1
        val dropped = messageDao.listForChat(message.chatId).filter { it.createdAt >= from }.flatMap { Attachment.decode(it.attachmentsJson) }
        messageDao.deleteFrom(message.chatId, from)
        attachments.delete(dropped)
    }

    suspend fun rename(chatId: String, title: String) = chatDao.rename(chatId, title)

    suspend fun renameByUser(chatId: String, title: String) = chatDao.renameByUser(chatId, title.trim())

    suspend fun moveToFolder(chatIds: Collection<String>, folderId: String?) = chatDao.moveToFolder(chatIds.toList(), folderId)

    suspend fun createFolder(name: String): FolderEntity {
        val folder = FolderEntity(id = UUID.randomUUID().toString(), name = name.trim(), createdAt = clock())
        folderDao.insert(folder)
        return folder
    }

    suspend fun renameFolder(folderId: String, name: String) = folderDao.rename(folderId, name.trim())

    suspend fun deleteFolder(folderId: String) = folderDao.delete(folderId)

    suspend fun setModel(chatId: String, selection: ModelSelection) =
        chatDao.setModel(chatId, selection.providerId, selection.modelId, selection.reasoningEffort?.wireName)

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
