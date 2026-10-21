package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageNode
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBranchesTest {
    private fun node(id: String, parent: String?, at: Long) = MessageNode(id, parent, at)

    private fun row(id: String, role: MessageRole, parent: String?, at: Long = 0) = MessageEntity(
        id = id, chatId = "chat", role = role, content = id, reasoning = null, status = MessageStatus.COMPLETE,
        errorText = null, createdAt = at, modelId = null, toolCallsJson = null, toolCallId = null, parentId = parent,
    )

    @Test
    fun `a message nobody rewrote has no versions`() {
        val users = listOf(node("u1", null, 1), node("u2", "a1", 3))

        assertTrue(MessageBranches.versions(users).isEmpty())
    }

    @Test
    fun `edited messages count as versions of each other in creation order`() {
        val users = listOf(node("u1", "a0", 5), node("u1-edit", "a0", 9), node("u1-again", "a0", 12), node("other", "a7", 6))

        val versions = MessageBranches.versions(users)

        assertEquals(MessageVersions(1, 3, null, "u1-edit"), versions["u1"])
        assertEquals(MessageVersions(2, 3, "u1", "u1-again"), versions["u1-edit"])
        assertEquals(MessageVersions(3, 3, "u1-edit", null), versions["u1-again"])
        assertNull(versions["other"])
    }

    @Test
    fun `the first message of a chat can have versions too`() {
        val versions = MessageBranches.versions(listOf(node("first", null, 1), node("first-edit", null, 4)))

        assertEquals(MessageVersions(2, 2, "first", null), versions["first-edit"])
    }

    @Test
    fun `switching to a version lands on the newest end of its branch`() {
        val nodes = listOf(
            node("u1", null, 1), node("a1", "u1", 2), node("u2", "a1", 3), node("a2", "u2", 4),
            node("u2-edit", "a1", 5), node("a2-edit", "u2-edit", 6), node("u3-edit", "a2-edit", 7), node("a3-edit", "u3-edit", 8),
        )

        assertEquals("a2", MessageBranches.newestLeaf(nodes, "u2"))
        assertEquals("a3-edit", MessageBranches.newestLeaf(nodes, "u2-edit"))
        assertEquals("a3-edit", MessageBranches.newestLeaf(nodes, "a3-edit"))
    }

    @Test
    fun `a fork keeps everything up to the end of the chosen reply`() {
        val path = listOf(
            row("u1", MessageRole.USER, null), row("a1", MessageRole.ASSISTANT, "u1"), row("t1", MessageRole.TOOL, "a1"),
            row("a1b", MessageRole.ASSISTANT, "t1"), row("u2", MessageRole.USER, "a1b"), row("a2", MessageRole.ASSISTANT, "u2"),
        )

        assertEquals(listOf("u1", "a1", "t1", "a1b"), MessageBranches.upToReply(path, "a1")?.map { it.id })
        assertEquals(path.map { it.id }, MessageBranches.upToReply(path, "a2")?.map { it.id })
        assertNull(MessageBranches.upToReply(path, "gone"))
    }

    @Test
    fun `copies get new ids and keep their links among themselves`() {
        val kept = listOf(row("u1", MessageRole.USER, null), row("a1", MessageRole.ASSISTANT, "u1"), row("u2", MessageRole.USER, "a1"))
        var counter = 0

        val copies = MessageBranches.copyTo("fork", kept) { "new-${++counter}" }

        assertEquals(listOf("new-1", "new-2", "new-3"), copies.map { it.id })
        assertEquals(listOf(null, "new-1", "new-2"), copies.map { it.parentId })
        assertTrue(copies.all { it.chatId == "fork" })
        assertEquals(kept.map { it.content }, copies.map { it.content })
    }
}
