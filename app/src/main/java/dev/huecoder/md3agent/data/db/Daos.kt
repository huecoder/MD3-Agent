package dev.huecoder.md3agent.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ChatSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val modelId: String?,
    val folderId: String?,
    val lastMessage: String?,
    val iconKey: String? = null,
    val colorKey: String? = null,
    val shapeKey: String? = null,
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val agentId: String? = null,
)

data class AttachedFiles(val role: MessageRole, val attachmentsJson: String, val createdAt: Long)

data class MessageNode(val id: String, val parentId: String?, val createdAt: Long)

data class MessageHit(
    val messageId: String,
    val chatId: String,
    val chatTitle: String,
    val role: MessageRole,
    val createdAt: Long,
    val content: String,
)

@Dao
interface ChatDao {
    @Query(
        """
        SELECT c.id, c.title, c.updatedAt, c.modelId, c.folderId, c.iconKey, c.colorKey, c.shapeKey, c.archivedAt, c.trashedAt, c.agentId,
            (SELECT m.content FROM messages m
             WHERE m.chatId = c.id AND m.role IN ('USER', 'ASSISTANT') AND m.content != ''
             ORDER BY m.createdAt DESC LIMIT 1) AS lastMessage
        FROM chats c
        ORDER BY c.updatedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<ChatSummary>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observe(id: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun get(id: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(chat: ChatEntity)

    @Query("UPDATE chats SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE chats SET title = :title, titleSource = 'USER' WHERE id = :id")
    suspend fun renameByUser(id: String, title: String)

    @Query(
        """
        UPDATE chats SET
            title = CASE WHEN titleSource = 'USER' THEN title ELSE :title END,
            titleSource = CASE WHEN titleSource = 'USER' THEN titleSource ELSE 'GENERATED' END,
            iconKey = COALESCE(iconKey, :iconKey),
            colorKey = COALESCE(colorKey, :colorKey),
            shapeKey = COALESCE(shapeKey, :shapeKey)
        WHERE id = :id
        """,
    )
    suspend fun applyGeneratedLabel(id: String, title: String, iconKey: String, colorKey: String, shapeKey: String)

    @Query("UPDATE chats SET iconKey = :iconKey, colorKey = :colorKey, shapeKey = :shapeKey WHERE id = :id")
    suspend fun setLook(id: String, iconKey: String, colorKey: String, shapeKey: String)

    @Query("UPDATE chats SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<String>, folderId: String?)

    @Query(
        """
        SELECT c.id FROM chats c
        WHERE c.title GLOB :query
            OR EXISTS (
                SELECT 1 FROM messages m
                WHERE m.chatId = c.id AND m.role IN ('USER', 'ASSISTANT') AND m.content GLOB :query
            )
        """,
    )
    suspend fun search(query: String): List<String>

    @Query("UPDATE chats SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE chats SET leafId = :leafId WHERE id = :id")
    suspend fun setLeaf(id: String, leafId: String)

    @Query("UPDATE chats SET providerId = :providerId, modelId = :modelId, reasoningEffort = :reasoningEffort WHERE id = :id")
    suspend fun setModel(id: String, providerId: String?, modelId: String?, reasoningEffort: String?)

    @Query("UPDATE chats SET agentId = :agentId WHERE id = :id")
    suspend fun setAgent(id: String, agentId: String)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE chats SET trashedAt = :trashedAt WHERE id IN (:ids)")
    suspend fun setTrashed(ids: List<String>, trashedAt: Long?)

    @Query("UPDATE chats SET archivedAt = :archivedAt WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, archivedAt: Long?)

    @Query("SELECT id FROM chats WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun trashedBefore(before: Long): List<String>

    @Query("SELECT id FROM chats WHERE trashedAt IS NOT NULL")
    suspend fun trashed(): List<String>

    @Query(
        """
        SELECT m.id AS messageId, m.chatId, c.title AS chatTitle, m.role, m.createdAt, m.content
        FROM messages m JOIN chats c ON c.id = m.chatId
        WHERE c.trashedAt IS NULL AND m.role IN ('USER', 'ASSISTANT') AND m.content GLOB :query
        ORDER BY m.createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchMessages(query: String, limit: Int): List<MessageHit>
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name, iconKey = :iconKey, colorKey = :colorKey WHERE id = :id")
    suspend fun update(id: String, name: String, iconKey: String?, colorKey: String?)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)
}

private const val ACTIVE_PATH = """
    WITH RECURSIVE path(id, parentId, depth) AS (
        SELECT m.id, m.parentId, 0 FROM messages m WHERE m.id = (SELECT leafId FROM chats WHERE id = :chatId)
        UNION ALL
        SELECT m.id, m.parentId, path.depth + 1 FROM messages m JOIN path ON m.id = path.parentId
    )
    SELECT m.* FROM messages m JOIN path ON m.id = path.id ORDER BY path.depth DESC
"""

@Dao
abstract class MessageDao {
    @Query(ACTIVE_PATH)
    abstract fun observePath(chatId: String): Flow<List<MessageEntity>>

    @Query(ACTIVE_PATH)
    abstract suspend fun path(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId")
    abstract suspend fun all(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    abstract suspend fun get(id: String): MessageEntity?

    @Query("SELECT id, parentId, createdAt FROM messages WHERE chatId = :chatId AND role = 'USER'")
    abstract fun observeUserNodes(chatId: String): Flow<List<MessageNode>>

    @Query("SELECT id, parentId, createdAt FROM messages WHERE chatId = :chatId")
    abstract suspend fun nodes(chatId: String): List<MessageNode>

    @Query("SELECT leafId FROM chats WHERE id = :chatId")
    abstract suspend fun leafOf(chatId: String): String?

    @Query("SELECT attachmentsJson FROM messages WHERE attachmentsJson IS NOT NULL")
    abstract suspend fun attachmentLists(): List<String>

    @Upsert
    abstract suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertChat(chat: ChatEntity)

    @Query("UPDATE chats SET leafId = :leafId, updatedAt = :updatedAt WHERE id = :chatId")
    abstract suspend fun moveLeaf(chatId: String, leafId: String, updatedAt: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    abstract suspend fun deleteAll(ids: List<String>)

    @Transaction
    open suspend fun add(message: MessageEntity, at: Long) {
        upsert(message)
        moveLeaf(message.chatId, message.id, at)
    }

    @Transaction
    open suspend fun cut(chatId: String, keepId: String, dropIds: List<String>, at: Long) {
        deleteAll(dropIds)
        moveLeaf(chatId, keepId, at)
    }

    @Transaction
    open suspend fun fork(chat: ChatEntity, messages: List<MessageEntity>) {
        insertChat(chat)
        insertAll(messages)
    }

    @Query(
        """
        SELECT m.role, m.attachmentsJson, m.createdAt FROM messages m JOIN chats c ON c.id = m.chatId
        WHERE m.attachmentsJson IS NOT NULL AND c.trashedAt IS NULL
        ORDER BY m.createdAt DESC
        """,
    )
    abstract suspend fun attachedFiles(): List<AttachedFiles>

    @Query("UPDATE messages SET status = 'CANCELLED' WHERE status = 'STREAMING'")
    abstract suspend fun markInterruptedStreams()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE tier = :tier ORDER BY createdAt ASC")
    fun observeTier(tier: MemoryTier): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY tier ASC, updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE tier = :tier ORDER BY createdAt ASC")
    suspend fun listTier(tier: MemoryTier): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: String): MemoryEntity?

    @Upsert
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAt ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY triggerAt ASC")
    suspend fun listAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String): Int
}
