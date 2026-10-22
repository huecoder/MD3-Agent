package dev.huecoder.md3agent.data.changes

import android.util.Log
import dev.huecoder.md3agent.data.db.RevisionDao
import dev.huecoder.md3agent.data.db.RevisionEntity
import dev.huecoder.md3agent.data.db.RevisionOperation
import dev.huecoder.md3agent.data.settings.FileScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FileHistory(
    private val dao: RevisionDao,
    private val blobs: RevisionBlobs,
    private val snapshots: FileSnapshots,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val netCounts = ConcurrentHashMap<String, DiffCounts>()

    suspend fun <T> recording(source: ChangeSource?, file: File, scope: FileScope, change: suspend () -> T): T {
        if (source == null) return change()
        val before = snapshots.capture(file, scope)
        val result = change()
        try {
            store(source, file, before, snapshots.capture(file, scope))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not record the change of ${file.path}", e)
        }
        return result
    }

    fun observe(chatId: String): Flow<List<RevisionEntity>> = dao.observe(chatId)

    suspend fun replyChanges(callIdsByReply: Map<String, Set<String>>, revisions: List<RevisionEntity>): Map<String, ReplyChanges> {
        val changes = LinkedHashMap<String, ReplyChanges>()
        for ((replyId, callIds) in callIdsByReply) {
            val files = revisions.filter { it.toolCallId in callIds }.groupBy { it.path }.values.mapNotNull { netChange(it) }
            if (files.isNotEmpty()) changes[replyId] = ReplyChanges(files)
        }
        return changes
    }

    suspend fun diff(change: FileChange): List<DiffLine>? = withContext(Dispatchers.IO) {
        val before = if (change.created) "" else blobs.read(change.firstId, RevisionBlobs.Side.BEFORE)?.decodeToString() ?: return@withContext null
        val after = blobs.read(change.lastId, RevisionBlobs.Side.AFTER)?.decodeToString() ?: return@withContext null
        LineDiff.compute(LineDiff.splitLines(before), LineDiff.splitLines(after))
    }

    suspend fun forget(chatId: String) {
        val ids = dao.idsOf(chatId)
        withContext(Dispatchers.IO) { blobs.delete(ids) }
    }

    private suspend fun store(source: ChangeSource, file: File, before: Snapshot, after: Snapshot) {
        val beforeBytes = (before as? Snapshot.Text)?.bytes
        val afterBytes = (after as? Snapshot.Text)?.bytes
        val readable = before !is Snapshot.Opaque && afterBytes != null
        if (readable && beforeBytes?.contentEquals(afterBytes) == true) return
        val id = newId()
        val counts = if (readable) LineDiff.counts(LineDiff.compute(lines(beforeBytes), lines(afterBytes))) else DiffCounts(0, 0)
        if (readable && afterBytes != null) {
            withContext(Dispatchers.IO) {
                blobs.write(id, RevisionBlobs.Side.AFTER, afterBytes)
                if (beforeBytes != null) blobs.write(id, RevisionBlobs.Side.BEFORE, beforeBytes)
            }
        }
        dao.add(
            RevisionEntity(
                id = id,
                chatId = source.chatId,
                toolCallId = source.toolCallId,
                path = file.path,
                createdAt = clock(),
                operation = if (before is Snapshot.Absent) RevisionOperation.CREATED else RevisionOperation.MODIFIED,
                beforeHash = (before as? Snapshot.Text)?.hash,
                afterHash = (after as? Snapshot.Text)?.hash.takeIf { readable },
                snapshots = readable,
                added = counts.added,
                removed = counts.removed,
            ),
        )
    }

    private suspend fun netChange(group: List<RevisionEntity>): FileChange? {
        val first = group.first()
        val last = group.last()
        val diffable = group.all { it.snapshots }
        if (diffable && first.beforeHash == last.afterHash) return null
        val counts = when {
            !diffable -> DiffCounts(0, 0)
            group.size == 1 -> DiffCounts(first.added, first.removed)
            else -> net(first, last)
        }
        return FileChange(
            path = first.path,
            created = first.operation == RevisionOperation.CREATED,
            counts = counts,
            revisionIds = group.map { it.id },
            diffable = diffable,
            rolledBack = group.all { it.rolledBack },
        )
    }

    private suspend fun net(first: RevisionEntity, last: RevisionEntity): DiffCounts {
        val key = "${first.id}>${last.id}"
        netCounts[key]?.let { return it }
        val lines = diff(FileChange(first.path, first.operation == RevisionOperation.CREATED, DiffCounts(0, 0), listOf(first.id, last.id), diffable = true, rolledBack = false))
        val counts = lines?.let(LineDiff::counts) ?: DiffCounts(first.added, first.removed)
        netCounts[key] = counts
        return counts
    }

    private fun lines(bytes: ByteArray?): List<String> = LineDiff.splitLines(bytes?.decodeToString().orEmpty())

    private companion object {
        const val TAG = "FileHistory"
    }
}
