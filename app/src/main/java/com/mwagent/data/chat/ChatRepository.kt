package com.mwagent.data.chat

import com.mwagent.data.attachments.Attachment
import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.db.ChatDao
import com.mwagent.data.db.ChatEntity
import com.mwagent.data.db.ChatSummary
import com.mwagent.data.db.FolderDao
import com.mwagent.data.db.FolderEntity
import com.mwagent.data.db.MessageDao
import com.mwagent.data.db.MessageEntity
import com.mwagent.data.db.MessageHit
import com.mwagent.data.db.MessageRole
import com.mwagent.data.db.MessageStatus
import com.mwagent.data.settings.ModelSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val folderDao: FolderDao,
    private val attachments: AttachmentStore,
    private val drafts: ChatDrafts,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSummaries(): Flow<List<ChatSummary>> = chatDao.observeSummaries()

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    suspend fun searchChatIds(query: String): Set<String> = chatDao.search(likePattern(query)).toSet()

    suspend fun searchMessages(query: String, limit: Int): List<MessageHit> = chatDao.searchMessages(likePattern(query), limit)

    val trashEvents: SharedFlow<Set<String>> get() = _trashEvents

    private val _trashEvents = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)

    fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observe(chatId)

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.observeForChat(chatId)

    suspend fun chat(chatId: String): ChatEntity? = chatDao.get(chatId)

    suspend fun history(chatId: String): List<MessageEntity> = messageDao.listForChat(chatId)

    suspend fun createChat(title: String, selection: ModelSelection, agentId: String): ChatEntity {
        val now = clock()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            providerId = selection.providerId,
            modelId = selection.modelId,
            reasoningEffort = selection.reasoningEffort?.wireName,
            agentId = agentId,
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

    suspend fun applyGeneratedLabel(chatId: String, title: String, look: ChatLook) =
        chatDao.applyGeneratedLabel(chatId, title.trim(), look.glyph.key, look.tint.key, look.shape.key)

    suspend fun setLook(chatId: String, look: ChatLook) = chatDao.setLook(chatId, look.glyph.key, look.tint.key, look.shape.key)

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

    suspend fun setAgent(chatId: String, agentId: String) = chatDao.setAgent(chatId, agentId)

    suspend fun trash(chatIds: Collection<String>) {
        chatDao.setTrashed(chatIds.toList(), clock())
        _trashEvents.tryEmit(chatIds.toSet())
    }

    suspend fun restore(chatIds: Collection<String>) = chatDao.setTrashed(chatIds.toList(), null)

    suspend fun archive(chatIds: Collection<String>) = chatDao.setArchived(chatIds.toList(), clock())

    suspend fun unarchive(chatIds: Collection<String>) = chatDao.setArchived(chatIds.toList(), null)

    suspend fun deleteForever(chatIds: Collection<String>) = chatIds.forEach { delete(it) }

    suspend fun emptyTrash() = deleteForever(chatDao.trashed())

    suspend fun purgeExpiredTrash() = deleteForever(chatDao.trashedBefore(clock() - TRASH_RETENTION_MS))

    private suspend fun delete(chatId: String) {
        val attached = messageDao.listForChat(chatId).flatMap { Attachment.decode(it.attachmentsJson) }
        chatDao.delete(chatId)
        drafts.forget(chatId)
        attachments.delete(attached)
    }

    fun draft(chatId: String?): String = drafts.read(chatId)

    fun saveDraft(chatId: String?, text: String) = drafts.write(chatId, text)

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

    private fun likePattern(query: String) = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }

    @Synchronized
    private fun monotonicNow(): Long {
        lastTimestamp = maxOf(clock(), lastTimestamp + 1)
        return lastTimestamp
    }
}
