package dev.huecoder.md3agent.data.db

import androidx.room.ColumnInfo
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [Index("updatedAt"), Index("folderId")],
    foreignKeys = [ForeignKey(entity = FolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL)],
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String?,
    val modelId: String?,
    val reasoningEffort: String? = null,
    val folderId: String? = null,
    val iconKey: String? = null,
    val colorKey: String? = null,
    val shapeKey: String? = null,
    @ColumnInfo(defaultValue = "FIRST_MESSAGE") val titleSource: TitleSource = TitleSource.FIRST_MESSAGE,
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val agentId: String? = null,
)

val ChatEntity.selection: ModelSelection?
    get() = if (providerId != null && !modelId.isNullOrBlank()) ModelSelection(providerId, modelId, reasoningEffort?.let(ReasoningEffort::fromWire)) else null

enum class TitleSource { FIRST_MESSAGE, GENERATED, USER }

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val iconKey: String? = null,
    val colorKey: String? = null,
)

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

enum class MessageStatus { COMPLETE, STREAMING, ERROR, CANCELLED }

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["chatId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: MessageRole,
    val content: String,
    val reasoning: String?,
    val status: MessageStatus,
    val errorText: String?,
    val createdAt: Long,
    val modelId: String?,
    val toolCallsJson: String?,
    val toolCallId: String?,
    val attachmentsJson: String? = null,
    val finishedAt: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cachedTokens: Int? = null,
    val contextNote: String? = null,
    val reasoningTraceJson: String? = null,
)

enum class MemoryTier { INSTANT, LONG_TERM }

@Entity(tableName = "memories", indices = [Index("tier")])
data class MemoryEntity(
    @PrimaryKey val id: String,
    val tier: MemoryTier,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val embedding: String? = null,
    val embeddingModel: String? = null,
)

@Entity(tableName = "reminders", indices = [Index("triggerAt")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val text: String,
    val triggerAt: Long,
    val chatId: String?,
    val createdAt: Long,
)
