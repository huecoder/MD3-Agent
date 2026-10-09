package dev.huecoder.md3agent.feature.home

import dev.huecoder.md3agent.data.settings.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowActionTest {
    @Test
    fun `settings drive swipes outside the trash`() {
        assertEquals(RowAction.TRASH, RowAction.resolve(SwipeAction.TRASH, ChatFilter.All, isRight = true))
        assertEquals(RowAction.ARCHIVE, RowAction.resolve(SwipeAction.ARCHIVE, ChatFilter.Folder("work"), isRight = false))
        assertNull(RowAction.resolve(SwipeAction.NONE, ChatFilter.All, isRight = true))
    }

    @Test
    fun `archive swipe takes chats back out of the archive`() {
        assertEquals(RowAction.UNARCHIVE, RowAction.resolve(SwipeAction.ARCHIVE, ChatFilter.Archive, isRight = false))
    }

    @Test
    fun `trash always restores right and deletes left`() {
        assertEquals(RowAction.RESTORE, RowAction.resolve(SwipeAction.NONE, ChatFilter.Trash, isRight = true))
        assertEquals(RowAction.DELETE_FOREVER, RowAction.resolve(SwipeAction.MOVE, ChatFilter.Trash, isRight = false))
    }
}
