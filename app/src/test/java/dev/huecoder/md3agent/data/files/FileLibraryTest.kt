package dev.huecoder.md3agent.data.files

import android.content.ContentResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class FileLibraryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val uploads by lazy { temp.newFolder("uploads") }
    private val workspace by lazy { temp.newFolder("workspace") }
    private val attachments by lazy { temp.newFolder("attachments") }
    private var sent = emptyList<SentFile>()
    private val library by lazy {
        FileLibrary(object : ContentResolver(null) {}, uploads, workspace, sentFiles = { sent }) { name ->
            if (name.endsWith(".png") || name.endsWith(".jpg")) "image/png" else "text/plain"
        }
    }

    private fun File.withBytes(text: String) = apply { parentFile?.mkdirs(); writeText(text) }

    private fun sentFile(name: String, text: String, byUser: Boolean, at: Long): SentFile {
        val stored = File(attachments, "$at-$name").withBytes(text)
        return SentFile(stored.name, ViewableFile(stored, name, "image/png"), byUser, at)
    }

    @Test
    fun `media keeps one copy of each picture`() = runTest {
        File(uploads, "cat.png").withBytes("cat")
        File(workspace, "plots/cat-copy.png").withBytes("cat")
        File(workspace, "dog.jpg").withBytes("dog")
        File(workspace, "notes.txt").withBytes("cat")
        sent = listOf(sentFile("cat.png", "cat", byUser = true, at = 1), sentFile("bird.png", "bird", byUser = false, at = 2))

        val names = library.media().map { it.name }.sorted()

        assertEquals(3, names.size)
        assertTrue("dog.jpg" in names && "bird.png" in names)
    }

    @Test
    fun `each side sees only the chat files it sent`() = runTest {
        sent = listOf(sentFile("mine.png", "a", byUser = true, at = 1), sentFile("agents.png", "b", byUser = false, at = 2))

        val mine = library.list(LibraryLocation(LibraryRoot.MINE, fromChats = true)).map { it.name }
        val agents = library.list(LibraryLocation(LibraryRoot.AGENT, fromChats = true)).map { it.name }

        assertEquals(listOf("mine.png"), mine)
        assertEquals(listOf("agents.png"), agents)
        assertEquals(EntryKind.CHAT_FILES, library.list(LibraryLocation(LibraryRoot.MINE)).first().kind)
    }

    @Test
    fun `folders come first and hidden files stay hidden`() = runTest {
        File(workspace, "b.txt").withBytes("b")
        File(workspace, ".cache/x").withBytes("x")
        File(workspace, "a-folder").mkdirs()

        val entries = library.list(LibraryLocation(LibraryRoot.AGENT))

        assertEquals(listOf("a-folder", "b.txt"), entries.map { it.name })
    }

    @Test(expected = IOException::class)
    fun `locations cannot climb out of their root`() = runTest {
        library.list(LibraryLocation(LibraryRoot.MINE, listOf("..", "workspace")))
    }

    @Test(expected = InvalidFileNameException::class)
    fun `folder names cannot contain slashes`() = runTest {
        library.createFolder(LibraryLocation(LibraryRoot.MINE), "a/b")
    }

    @Test
    fun `rename and delete work inside the user's folder`() = runTest {
        File(uploads, "draft.md").withBytes("x")
        val location = LibraryLocation(LibraryRoot.MINE)
        library.rename(location, library.list(location).single(), "final.md")
        assertEquals(listOf("final.md"), library.list(location).map { it.name })
        library.delete(location, library.list(location))
        assertTrue(library.list(location).isEmpty())
    }
}
