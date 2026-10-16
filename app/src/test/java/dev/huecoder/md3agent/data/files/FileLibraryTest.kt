package dev.huecoder.md3agent.data.files

import android.content.ContentResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files

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
    fun `media skips chat pictures whose file is gone`() = runTest {
        sent = listOf(sentFile("a.png", "a", byUser = true, at = 1), sentFile("b.png", "b", byUser = false, at = 2))
        File(attachments, "1-a.png").delete()

        assertEquals(listOf("b.png"), library.media().map { it.name })
    }

    @Test
    fun `deleting a picture removes every copy of it and nothing else`() = runTest {
        File(uploads, "cat.png").withBytes("cat")
        File(workspace, "plots/cat-copy.png").withBytes("cat")
        File(workspace, "dog.png").withBytes("dog")
        File(workspace, "notes.txt").withBytes("cat")
        sent = listOf(sentFile("cat.png", "cat", byUser = true, at = 1))
        val cat = library.media().first { it.name.startsWith("cat") }

        library.deleteMedia(listOf(cat))

        assertEquals(listOf("dog.png"), library.media().map { it.name })
        assertFalse(File(uploads, "cat.png").exists())
        assertFalse(File(workspace, "plots/cat-copy.png").exists())
        assertFalse(File(attachments, "1-cat.png").exists())
        assertTrue(File(workspace, "notes.txt").exists())
    }

    @Test
    fun `several pictures are deleted in one go`() = runTest {
        File(uploads, "a.png").withBytes("a")
        File(uploads, "b.png").withBytes("b")
        File(uploads, "c.png").withBytes("c")
        val shown = library.media()

        library.deleteMedia(shown.filter { it.name != "c.png" })

        assertEquals(listOf("c.png"), library.media().map { it.name })
    }

    @Test
    fun `deleting a picture that is already gone is not an error`() = runTest {
        File(uploads, "gone.png").withBytes("x")
        val shown = library.media()
        File(uploads, "gone.png").delete()

        library.deleteMedia(shown)

        assertTrue(library.media().isEmpty())
    }

    @Test
    fun `entries are keyed by file so chat files with the same name stay apart`() = runTest {
        sent = listOf(sentFile("shot.png", "a", byUser = true, at = 1), sentFile("shot.png", "b", byUser = true, at = 2))

        val entries = library.list(LibraryLocation(LibraryRoot.MINE, fromChats = true))

        assertEquals(2, entries.map { it.key }.toSet().size)
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

    @Test
    fun `a folder with nested content and a read-only directory inside is deleted`() = runTest {
        File(workspace, "project/src/deep/a.txt").withBytes("a")
        File(workspace, "project/src/b.txt").withBytes("b")
        File(workspace, "project/src").setWritable(false)
        File(workspace, "project/src/deep").setWritable(false)
        val location = LibraryLocation(LibraryRoot.AGENT)

        library.delete(location, library.list(location))

        assertTrue(library.list(location).isEmpty())
    }

    @Test
    fun `deleting a folder removes a symlink inside it but never what it points to`() = runTest {
        val outside = temp.newFolder("outside").also { File(it, "keep.txt").withBytes("keep") }
        File(workspace, "linked").mkdirs()
        Files.createSymbolicLink(File(workspace, "linked/shortcut").toPath(), outside.toPath())
        val location = LibraryLocation(LibraryRoot.AGENT)

        library.delete(location, library.list(location))

        assertTrue(library.list(location).isEmpty())
        assertEquals("keep", File(outside, "keep.txt").readText())
    }

    @Test
    fun `deleting something that is already gone is not an error`() = runTest {
        File(uploads, "gone.txt").withBytes("x")
        val location = LibraryLocation(LibraryRoot.MINE)
        val listed = library.list(location)
        File(uploads, "gone.txt").delete()

        library.delete(location, listed)

        assertTrue(library.list(location).isEmpty())
    }

    @Test
    fun `the rest of a folder is still deleted when one entry cannot be removed`() {
        val tree = temp.newFolder("tree")
        File(tree, "free.txt").withBytes("x")
        File(tree, "stuck/inner.txt").withBytes("y")
        File(tree, "other/more.txt").withBytes("z")
        val stuckFile = File(tree, "stuck/inner.txt").toPath()

        val failure = runCatching {
            tree.deleteTree { path -> if (path == stuckFile) throw AccessDeniedException(path.toString()) else Files.deleteIfExists(path) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(!File(tree, "free.txt").exists())
        assertTrue(!File(tree, "other").exists())
        assertTrue(File(tree, "stuck/inner.txt").exists())
        assertTrue(tree.exists())
    }

    private fun impossibleEntry(name: String) = LibraryEntry(EntryKind.FILE, name, null, 0, 0, isReadOnly = false)

    private fun libraryWith(removal: RemovalFallback) =
        FileLibrary(object : ContentResolver(null) {}, uploads, workspace, sentFiles = { sent }, removalFallback = removal)

    @Test
    fun `a delete that cannot be done says what failed and why`() = runTest {
        File(workspace, "plain.txt").withBytes("x")
        val location = LibraryLocation(LibraryRoot.AGENT)

        val failure = runCatching { libraryWith(RemovalFallback.None).delete(location, listOf(impossibleEntry("plain.txt/inside"))) }.exceptionOrNull()

        assertTrue(failure is DeleteFailedException)
        assertEquals(DeleteReason.OTHER, (failure as DeleteFailedException).reason)
        assertEquals("Not a directory", failure.detail)
        assertTrue(File(workspace, "plain.txt").exists())
    }

    @Test
    fun `the fallback steps in only after the normal delete failed`() = runTest {
        File(workspace, "plain.txt").withBytes("x")
        File(workspace, "fine.txt").withBytes("y")
        val asked = mutableListOf<String>()
        val library = libraryWith { file -> asked += file.name; true }
        val location = LibraryLocation(LibraryRoot.AGENT)

        library.delete(location, listOf(impossibleEntry("plain.txt/inside"), library.list(location).first { it.name == "fine.txt" }))

        assertEquals(listOf("inside"), asked)
        assertFalse(File(workspace, "fine.txt").exists())
    }

    @Test
    fun `a refused fallback keeps the original failure`() = runTest {
        File(workspace, "plain.txt").withBytes("x")
        val library = libraryWith { false }

        val failure = runCatching { library.delete(LibraryLocation(LibraryRoot.AGENT), listOf(impossibleEntry("plain.txt/inside"))) }.exceptionOrNull()

        assertTrue(failure is DeleteFailedException)
    }

    @Test
    fun `failures are described by their kind and shown relative to the deleted item`() {
        val doomed = File(workspace, "project")
        val nested = File(doomed, "src/deep/a.txt")

        val denied = doomed.describeFailure(AccessDeniedException(nested.path))
        val notEmpty = doomed.describeFailure(DirectoryNotEmptyException(File(doomed, "src").path))
        val other = doomed.describeFailure(IOException("disk on fire"))

        assertEquals("project/src/deep/a.txt", denied.path)
        assertEquals(DeleteReason.PERMISSION_DENIED, denied.reason)
        assertEquals("project/src", notEmpty.path)
        assertEquals(DeleteReason.NOT_EMPTY, notEmpty.reason)
        assertEquals("project", other.path)
        assertEquals("disk on fire", other.detail)
    }
}
