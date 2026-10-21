package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageNode
import dev.huecoder.md3agent.data.db.MessageRole
import java.util.UUID

data class MessageVersions(val position: Int, val count: Int, val previousId: String?, val nextId: String?)

object MessageBranches {
    fun versions(userNodes: List<MessageNode>): Map<String, MessageVersions> =
        userNodes.groupBy { it.parentId }.values.filter { it.size > 1 }.flatMap { siblings ->
            val ordered = siblings.sortedBy { it.createdAt }
            ordered.mapIndexed { index, node ->
                node.id to MessageVersions(index + 1, ordered.size, ordered.getOrNull(index - 1)?.id, ordered.getOrNull(index + 1)?.id)
            }
        }.toMap()

    fun newestLeaf(nodes: List<MessageNode>, from: String): String {
        val children = nodes.groupBy { it.parentId }
        var current = from
        while (true) {
            current = children[current]?.maxByOrNull { it.createdAt }?.id ?: return current
        }
    }

    fun upToReply(path: List<MessageEntity>, replyId: String): List<MessageEntity>? {
        val start = path.indexOfFirst { it.id == replyId }.takeIf { it >= 0 } ?: return null
        val nextUser = path.drop(start + 1).indexOfFirst { it.role == MessageRole.USER }
        return path.take(if (nextUser < 0) path.size else start + 1 + nextUser)
    }

    fun copyTo(chatId: String, messages: List<MessageEntity>, newId: () -> String = { UUID.randomUUID().toString() }): List<MessageEntity> {
        val ids = messages.associate { it.id to newId() }
        return messages.map { it.copy(id = ids.getValue(it.id), chatId = chatId, parentId = it.parentId?.let(ids::get)) }
    }
}
