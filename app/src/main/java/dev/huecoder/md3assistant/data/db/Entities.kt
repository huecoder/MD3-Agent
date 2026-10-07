package dev.huecoder.md3assistant.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chats", indices = [Index("updatedAt")])
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String?,
    val modelId: String?,
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
)
