package dev.huecoder.md3agent.assistant

import dev.huecoder.md3agent.data.files.ViewableFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AttachMenuStateTest {
    private val menu = AttachMenuState()

    private fun file(name: String) = ViewableFile(File(name), name, "text/plain")

    @Test
    fun `back leaves folders one by one, then the file picker, then the menu`() {
        menu.open()
        menu.openAppFiles()
        menu.files.select(AppFilesTab.AGENT)
        menu.files.enter("reports")
        menu.files.enter("2026")

        assertTrue(menu.back())
        assertEquals(listOf("reports"), menu.files.folder)
        assertTrue(menu.back())
        assertTrue(menu.files.folder.isEmpty())
        assertEquals(AttachPage.APP_FILES, menu.page)
        assertTrue(menu.back())
        assertEquals(AttachPage.SOURCES, menu.page)
        assertTrue(menu.back())
        assertFalse(menu.isOpen)
    }

    @Test
    fun `back does nothing while the menu is closed`() {
        assertFalse(menu.back())
    }

    @Test
    fun `switching the tab leaves the folder but keeps the chosen files`() {
        menu.files.select(AppFilesTab.MINE)
        menu.files.enter("docs")
        menu.files.toggle(file("todo.txt"))

        menu.files.select(AppFilesTab.CHATS)

        assertTrue(menu.files.folder.isEmpty())
        assertEquals(listOf(file("todo.txt")), menu.files.chosen.toList())
    }

    @Test
    fun `choosing a file twice takes it back`() {
        menu.files.toggle(file("a.txt"))
        menu.files.toggle(file("b.txt"))
        menu.files.toggle(file("a.txt"))

        assertEquals(listOf(file("b.txt")), menu.files.chosen.toList())
    }

    @Test
    fun `opening the menu again starts from the sources with nothing chosen`() {
        menu.open()
        menu.openAppFiles()
        menu.files.select(AppFilesTab.AGENT)
        menu.files.toggle(file("a.txt"))
        menu.close()

        menu.open()

        assertEquals(AttachPage.SOURCES, menu.page)
        assertEquals(AppFilesTab.CHATS, menu.files.tab)
        assertTrue(menu.files.chosen.isEmpty())
    }
}
