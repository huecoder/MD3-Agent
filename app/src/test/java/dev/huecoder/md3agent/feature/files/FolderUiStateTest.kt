package dev.huecoder.md3agent.feature.files

import dev.huecoder.md3agent.data.files.EntryKind
import dev.huecoder.md3agent.data.files.LibraryEntry
import dev.huecoder.md3agent.data.files.ViewableFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FolderUiStateTest {
    private fun file(name: String, readOnly: Boolean = false, parent: String = "/library") =
        LibraryEntry(EntryKind.FILE, name, ViewableFile(File(parent, name), name, "text/plain"), 1, 1, isReadOnly = readOnly)

    private val folder = LibraryEntry(EntryKind.FOLDER, "docs", null, 0, 1, isReadOnly = false)
    private val chatFolder = LibraryEntry(EntryKind.CHAT_FILES, "", null, 0, 0, isReadOnly = true)

    private fun state(vararg selected: LibraryEntry, entries: List<LibraryEntry>) =
        FolderUiState(entries = entries, selection = selected.map { it.key }.toSet(), isLoaded = true)

    @Test
    fun `files can be shared and deleted together`() {
        val a = file("a.txt")
        val b = file("b.txt")
        val state = state(a, b, entries = listOf(a, b))

        assertTrue(state.canShare)
        assertTrue(state.canDelete)
        assertFalse(state.canRename)
        assertEquals(listOf(a.file, b.file), state.sharedFiles)
    }

    @Test
    fun `a folder in the selection hides sharing but not deleting`() {
        val a = file("a.txt")
        val state = state(a, folder, entries = listOf(folder, a))

        assertFalse(state.canShare)
        assertTrue(state.canDelete)
    }

    @Test
    fun `files from chats can be shared but neither renamed nor deleted`() {
        val sent = file("shot.png", readOnly = true, parent = "/attachments")
        val state = state(sent, entries = listOf(chatFolder, sent))

        assertTrue(state.canShare)
        assertFalse(state.canDelete)
        assertFalse(state.canRename)
    }

    @Test
    fun `one deletable entry can be renamed`() {
        val a = file("a.txt")

        assertTrue(state(a, entries = listOf(a)).canRename)
        assertTrue(state(folder, entries = listOf(folder)).canRename)
    }

    @Test
    fun `the chat folder cannot be selected`() {
        val a = file("a.txt")
        val state = FolderUiState(entries = listOf(chatFolder, folder, a))

        assertEquals(listOf(folder.key, a.key), state.selectableKeys)
    }

    @Test
    fun `nothing selected offers no actions`() {
        val state = FolderUiState(entries = listOf(file("a.txt")))

        assertFalse(state.isSelecting)
        assertFalse(state.canShare)
        assertFalse(state.canDelete)
        assertFalse(state.canRename)
    }

    @Test
    fun `chat files with one name stay separate in the selection`() {
        val first = file("shot.png", readOnly = true, parent = "/attachments/1")
        val second = file("shot.png", readOnly = true, parent = "/attachments/2")
        val state = state(first, entries = listOf(first, second))

        assertEquals(listOf(first), state.selectedEntries)
    }
}
