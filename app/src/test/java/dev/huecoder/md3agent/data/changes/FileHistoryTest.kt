package dev.huecoder.md3agent.data.changes

import dev.huecoder.md3agent.data.db.RevisionDao
import dev.huecoder.md3agent.data.db.RevisionEntity
import dev.huecoder.md3agent.data.db.RevisionOperation
import dev.huecoder.md3agent.data.settings.FileScope
import dev.huecoder.md3agent.system.files.DeviceFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileHistoryTest {
    private class FakeDao : RevisionDao {
        val rows = mutableListOf<RevisionEntity>()

        override suspend fun add(revision: RevisionEntity) {
            rows += revision
        }

        override fun observe(chatId: String): Flow<List<RevisionEntity>> = flowOf(rows.filter { it.chatId == chatId })

        override suspend fun byIds(ids: List<String>) = rows.filter { it.id in ids }

        override suspend fun idsOf(chatId: String) = rows.filter { it.chatId == chatId }.map { it.id }

        override suspend fun markRolledBack(ids: List<String>, value: Boolean) {
            rows.replaceAll { if (it.id in ids) it.copy(rolledBack = value) else it }
        }
    }

    private lateinit var root: File
    private lateinit var dao: FakeDao
    private lateinit var files: DeviceFiles
    private lateinit var history: FileHistory
    private lateinit var rollback: ChangeRollback
    private var ids = 0
    private var now = 0L

    @Before
    fun setUp() {
        root = Files.createTempDirectory("history").toFile()
        dao = FakeDao()
        files = DeviceFiles(File(root, "ws"), File(root, "att"), File(root, "stage"), File(root, "up"), sharedStorage = File(root, "shared"))
        val snapshots = FileSnapshots(files)
        val blobs = RevisionBlobs(File(root, "blobs"))
        history = FileHistory(dao, blobs, snapshots, clock = { ++now }, newId = { "r${++ids}" })
        rollback = ChangeRollback(dao, blobs, files, snapshots)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(path: String, text: String, call: String? = "c${ids + 1}"): File {
        val file = files.resolve(path)
        runBlocking {
            history.recording(call?.let { ChangeSource("chat", it) }, file, FileScope.APP) { files.write(file, FileScope.APP, text.encodeToByteArray(), append = false) }
        }
        return file
    }

    private fun changes(vararg calls: String) = runBlocking { history.replyChanges(mapOf("reply" to calls.toSet()), dao.rows) }["reply"]

    @Test
    fun `a new file is recorded as created`() {
        write("notes.md", "a\nb\n", call = "c1")
        val change = changes("c1")!!.files.single()
        assertTrue(change.created)
        assertEquals(DiffCounts(2, 0), change.counts)
    }

    @Test
    fun `an edit records the diff against the old text`() {
        write("notes.md", "a\nb\nc\n", call = "c1")
        write("notes.md", "a\nB\nc\n", call = "c2")
        val change = changes("c2")!!.files.single()
        assertFalse(change.created)
        assertEquals(DiffCounts(1, 1), change.counts)
    }

    @Test
    fun `rewriting the same text records nothing`() {
        write("notes.md", "a\n", call = "c1")
        write("notes.md", "a\n", call = "c2")
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `calls without a chat source are not recorded`() {
        write("notes.md", "a\n", call = null)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `two edits in one reply count against the first text`() {
        write("notes.md", "a\nb\n", call = "c0")
        write("notes.md", "a\nX\n", call = "c1")
        write("notes.md", "a\nb\nc\n", call = "c2")
        val change = changes("c1", "c2")!!.files.single()
        assertEquals(DiffCounts(1, 0), change.counts)
    }

    @Test
    fun `an edit that returns to the old text has no net change`() {
        write("notes.md", "a\n", call = "c0")
        write("notes.md", "b\n", call = "c1")
        write("notes.md", "a\n", call = "c2")
        assertNull(changes("c1", "c2"))
    }

    @Test
    fun `binary files are listed without a diff`() {
        val file = files.resolve("pic.bin")
        runBlocking {
            history.recording(ChangeSource("chat", "c1"), file, FileScope.APP) { files.write(file, FileScope.APP, byteArrayOf(1, 0, 2), append = false) }
        }
        val change = changes("c1")!!.files.single()
        assertFalse(change.diffable)
    }

    @Test
    fun `undo restores the old text and redo brings the new one back`() {
        val file = write("notes.md", "old\n", call = "c0")
        write("notes.md", "new\n", call = "c1")
        val change = changes("c1")!!.files.single()
        val plans = runBlocking { rollback.plan(listOf(change), ChangeDirection.UNDO, FileScope.APP) }
        assertEquals(ChangeVerdict.READY, plans.single().verdict)
        val outcome = runBlocking { rollback.apply(plans, emptySet(), ChangeDirection.UNDO, FileScope.APP) }
        assertTrue(outcome.single() is ChangeOutcome.Done)
        assertEquals("old\n", file.readText())
        assertTrue(changes("c1")!!.rolledBack)
        val redo = runBlocking { rollback.plan(listOf(changes("c1")!!.files.single()), ChangeDirection.REDO, FileScope.APP) }
        assertEquals(ChangeVerdict.READY, redo.single().verdict)
        runBlocking { rollback.apply(redo, emptySet(), ChangeDirection.REDO, FileScope.APP) }
        assertEquals("new\n", file.readText())
        assertFalse(changes("c1")!!.rolledBack)
    }

    @Test
    fun `undo of a created file deletes it`() {
        val file = write("fresh.md", "x\n", call = "c1")
        val change = changes("c1")!!.files.single()
        val plans = runBlocking { rollback.plan(listOf(change), ChangeDirection.UNDO, FileScope.APP) }
        runBlocking { rollback.apply(plans, emptySet(), ChangeDirection.UNDO, FileScope.APP) }
        assertFalse(file.exists())
    }

    @Test
    fun `a file edited afterwards is not touched without consent`() {
        val file = write("notes.md", "old\n", call = "c0")
        write("notes.md", "new\n", call = "c1")
        val change = changes("c1")!!.files.single()
        file.writeText("edited by hand\n")
        val plans = runBlocking { rollback.plan(listOf(change), ChangeDirection.UNDO, FileScope.APP) }
        assertEquals(ChangeVerdict.CHANGED_SINCE, plans.single().verdict)
        val skipped = runBlocking { rollback.apply(plans, emptySet(), ChangeDirection.UNDO, FileScope.APP) }
        assertTrue(skipped.single() is ChangeOutcome.Skipped)
        assertEquals("edited by hand\n", file.readText())
        val forced = runBlocking { rollback.apply(plans, setOf(change.path), ChangeDirection.UNDO, FileScope.APP) }
        assertTrue(forced.single() is ChangeOutcome.Done)
        assertEquals("old\n", file.readText())
    }

    @Test
    fun `diff shows the old and new lines`() {
        write("notes.md", "a\nb\n", call = "c0")
        write("notes.md", "a\nc\n", call = "c1")
        val lines = runBlocking { history.diff(changes("c1")!!.files.single()) }!!
        assertEquals(listOf(DiffKind.SAME, DiffKind.REMOVED, DiffKind.ADDED), lines.map { it.kind })
    }

    @Test
    fun `forgetting a chat removes its saved copies`() {
        write("notes.md", "a\n", call = "c1")
        runBlocking { history.forget("chat") }
        assertEquals(0, File(root, "blobs").listFiles().orEmpty().size)
    }
}
