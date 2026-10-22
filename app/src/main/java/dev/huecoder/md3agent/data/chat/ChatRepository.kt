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
import dev.huecoder.md3agent.data.db.MessageHit
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.db.TitleSource
import dev.huecoder.md3agent.data.settings.ModelSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.util.UUID

data class ChatBranch(val messages: List<MessageEntity>, val versions: Map<String, MessageVersions>)

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

    fun observeBranch(chatId: String): Flow<ChatBranch> =
        combine(messageDao.observePath(chatId), messageDao.observeUserNodes(chatId)) { messages, userNodes ->
            ChatBranch(messages, MessageBranches.versions(userNodes))
        }

    suspend fun chat(chatId: String): ChatEntity? = chatDao.get(chatId)

    suspend fun history(chatId: String): List<MessageEntity> = messageDao.path(chatId)

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
        append(
            newMessage(chatId, MessageRole.USER, text, MessageStatus.COMPLETE, modelId = null)
                .copy(attachmentsJson = Attachment.encode(attached), contextNote = contextNote),
        )

    suspend fun appendToolResult(chatId: String, toolCallId: String, content: String, status: MessageStatus, attached: List<Attachment>): MessageEntity =
        append(
            newMessage(chatId, MessageRole.TOOL, content, status, modelId = null)
                .copy(toolCallId = toolCallId, attachmentsJson = Attachment.encode(attached)),
        )

    suspend fun startAssistantMessage(chatId: String, modelId: String): MessageEntity =
        append(newMessage(chatId, MessageRole.ASSISTANT, content = "", MessageStatus.STREAMING, modelId))

    suspend fun editUserMessage(messageId: String, text: String, added: List<Attachment>): MessageEntity? {
        val original = messageDao.get(messageId) ?: return null
        val content = text.trim()
        val attached = Attachment.decode(original.attachmentsJson) + added
        if (added.isEmpty() && content == original.content) return null
        if (content.isEmpty() && attached.isEmpty()) return null
        val edited = original.copy(
            id = UUID.randomUUID().toString(),
            content = content,
            attachmentsJson = Attachment.encode(attached),
            createdAt = monotonicNow(),
        )
        messageDao.add(edited, clock())
        return edited
    }

    suspend fun selectVersion(chatId: String, messageId: String) {
        chatDao.setLeaf(chatId, MessageBranches.newestLeaf(messageDao.nodes(chatId), messageId))
    }

    suspend fun fork(chatId: String, replyId: String, title: String): ChatEntity? {
        val source = chatDao.get(chatId) ?: return null
        val kept = MessageBranches.upToReply(messageDao.path(chatId), replyId) ?: return null
        val forkId = UUID.randomUUID().toString()
        val copies = MessageBranches.copyTo(forkId, kept)
        val now = clock()
        val fork = source.copy(
            id = forkId,
            title = title.trim(),
            createdAt = now,
            updatedAt = now,
            titleSource = TitleSource.USER,
            archivedAt = null,
            trashedAt = null,
            leafId = copies.last().id,
        )
        messageDao.fork(fork, copies)
        return fork
    }

    suspend fun reopen(failed: MessageEntity, modelId: String): MessageEntity =
        failed.copy(
            status = MessageStatus.STREAMING,
            errorText = null,
            modelId = modelId,
            toolCallsJson = null,
            reasoningTraceJson = null,
            finishedAt = null,
            promptTokens = null,
            completionTokens = null,
            cachedTokens = null,
        ).also { save(it) }

    suspend fun save(message: MessageEntity) {
        messageDao.upsert(message)
        chatDao.touch(message.chatId, clock())
    }

    suspend fun truncateAfter(message: MessageEntity) {
        val dropped = messageDao.path(message.chatId).dropWhile { it.id != message.id }.drop(1)
        if (dropped.isEmpty()) return
        messageDao.cut(message.chatId, message.id, dropped.map { it.id }, clock())
        releaseUnused(dropped.flatMap { Attachment.decode(it.attachmentsJson) })
    }

    suspend fun rename(chatId: String, title: String) = chatDao.rename(chatId, title)

    suspend fun renameByUser(chatId: String, title: String) = chatDao.renameByUser(chatId, title.trim())

    suspend fun applyGeneratedLabel(chatId: String, title: String, look: ChatLook) =
        chatDao.applyGeneratedLabel(chatId, title.trim(), look.glyph.key, look.tint.key, look.shape.key)

    suspend fun setLook(chatId: String, look: ChatLook) = chatDao.setLook(chatId, look.glyph.key, look.tint.key, look.shape.key)

    suspend fun moveToFolder(chatIds: Collection<String>, folderId: String?) = chatDao.moveToFolder(chatIds.toList(), folderId)

    suspend fun createFolder(name: String, look: FolderLook): FolderEntity {
        val folder = FolderEntity(id = UUID.randomUUID().toString(), name = name.trim(), createdAt = clock(), iconKey = look.glyph?.key, colorKey = look.tint.key)
        folderDao.insert(folder)
        return folder
    }

    suspend fun updateFolder(folderId: String, name: String, look: FolderLook) =
        folderDao.update(folderId, name.trim(), look.glyph?.key, look.tint.key)

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
        val attached = messageDao.all(chatId).flatMap { Attachment.decode(it.attachmentsJson) } + drafts.read(chatId).attachments
        chatDao.delete(chatId)
        drafts.forget(chatId)
        releaseUnused(attached)
    }

    private suspend fun releaseUnused(candidates: List<Attachment>) {
        if (candidates.isEmpty()) return
        val inUse = messageDao.attachmentLists().flatMapTo(HashSet()) { json -> Attachment.decode(json).map(Attachment::id) }
        attachments.delete(candidates.filter { it.id !in inUse })
    }

    private suspend fun append(message: MessageEntity): MessageEntity {
        val linked = message.copy(parentId = messageDao.leafOf(message.chatId))
        messageDao.add(linked, clock())
        return linked
    }

    fun observeDrafts(): StateFlow<Map<String, ChatDraft>> = drafts.all

    fun draft(chatId: String?): ChatDraft = drafts.read(chatId)

    fun saveDraft(chatId: String?, text: String, attached: List<Attachment>) = drafts.write(chatId, text, attached)

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
