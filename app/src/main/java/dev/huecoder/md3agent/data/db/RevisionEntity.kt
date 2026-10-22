package dev.huecoder.md3agent.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class RevisionOperation { CREATED, MODIFIED }

@Entity(
    tableName = "file_revisions",
    foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["chatId", "createdAt"])],
)
data class RevisionEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val toolCallId: String,
    val path: String,
    val createdAt: Long,
    val operation: RevisionOperation,
    val beforeHash: String?,
    val afterHash: String?,
    val snapshots: Boolean,
    val added: Int,
    val removed: Int,
    @ColumnInfo(defaultValue = "0") val rolledBack: Boolean = false,
)

@Dao
interface RevisionDao {
    @Insert
    suspend fun add(revision: RevisionEntity)

    @Query("SELECT * FROM file_revisions WHERE chatId = :chatId ORDER BY createdAt, rowid")
    fun observe(chatId: String): Flow<List<RevisionEntity>>

    @Query("SELECT * FROM file_revisions WHERE id IN (:ids) ORDER BY createdAt, rowid")
    suspend fun byIds(ids: List<String>): List<RevisionEntity>

    @Query("SELECT id FROM file_revisions WHERE chatId = :chatId")
    suspend fun idsOf(chatId: String): List<String>

    @Query("UPDATE file_revisions SET rolledBack = :value WHERE id IN (:ids)")
    suspend fun markRolledBack(ids: List<String>, value: Boolean)
}
