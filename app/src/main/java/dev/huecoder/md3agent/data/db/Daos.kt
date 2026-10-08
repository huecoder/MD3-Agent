package dev.huecoder.md3agent.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
)

@Dao
interface ChatDao {
    @Query(
        """
        SELECT c.id, c.title, c.updatedAt, c.modelId, c.folderId, c.iconKey, c.colorKey, c.shapeKey,
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
        WHERE c.title LIKE '%' || :query || '%' ESCAPE '\'
            OR EXISTS (
                SELECT 1 FROM messages m
                WHERE m.chatId = c.id AND m.role IN ('USER', 'ASSISTANT') AND m.content LIKE '%' || :query || '%' ESCAPE '\'
            )
        """,
    )
    suspend fun search(query: String): List<String>

    @Query("UPDATE chats SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE chats SET providerId = :providerId, modelId = :modelId, reasoningEffort = :reasoningEffort WHERE id = :id")
    suspend fun setModel(id: String, providerId: String?, modelId: String?, reasoningEffort: String?)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun listForChat(chatId: String): List<MessageEntity>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND createdAt >= :fromCreatedAt")
    suspend fun deleteFrom(chatId: String, fromCreatedAt: Long)

    @Query("UPDATE messages SET status = 'CANCELLED' WHERE status = 'STREAMING'")
    suspend fun markInterruptedStreams()
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
