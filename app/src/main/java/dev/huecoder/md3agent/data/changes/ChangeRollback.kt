package dev.huecoder.md3agent.data.changes

import dev.huecoder.md3agent.data.db.RevisionDao
import dev.huecoder.md3agent.data.db.RevisionEntity
import dev.huecoder.md3agent.data.settings.FileScope
import dev.huecoder.md3agent.system.files.DeviceFiles
import dev.huecoder.md3agent.system.files.FileAccessException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ChangeRollback(
    private val dao: RevisionDao,
    private val blobs: RevisionBlobs,
    private val files: DeviceFiles,
    private val snapshots: FileSnapshots,
) {
    suspend fun plan(changes: List<FileChange>, direction: ChangeDirection, scope: FileScope): List<ChangePlan> =
        changes.map { ChangePlan(it, verdict(it, direction, scope)) }

    suspend fun apply(plans: List<ChangePlan>, overwrite: Set<String>, direction: ChangeDirection, scope: FileScope): List<ChangeOutcome> =
        plans.map { plan ->
            val path = plan.change.path
            when {
                plan.verdict == ChangeVerdict.UNAVAILABLE -> ChangeOutcome.Failed(path, null)
                plan.verdict == ChangeVerdict.CHANGED_SINCE && path !in overwrite -> ChangeOutcome.Skipped(path)
                else -> restore(plan.change, direction, scope)
            }
        }

    private suspend fun verdict(change: FileChange, direction: ChangeDirection, scope: FileScope): ChangeVerdict {
        if (!change.diffable) return ChangeVerdict.UNAVAILABLE
        val (first, last) = ends(change) ?: return ChangeVerdict.UNAVAILABLE
        if (target(first, last, direction, change.created) == Missing) return ChangeVerdict.UNAVAILABLE
        val expected = if (direction == ChangeDirection.UNDO) last.afterHash else first.beforeHash
        val matches = when (val current = snapshots.capture(File(change.path), scope)) {
            Snapshot.Absent -> expected == null
            is Snapshot.Text -> current.hash == expected
            Snapshot.Opaque -> false
        }
        return if (matches) ChangeVerdict.READY else ChangeVerdict.CHANGED_SINCE
    }

    private suspend fun restore(change: FileChange, direction: ChangeDirection, scope: FileScope): ChangeOutcome {
        val (first, last) = ends(change) ?: return ChangeOutcome.Failed(change.path, null)
        val file = File(change.path)
        return try {
            when (val bytes = target(first, last, direction, change.created)) {
                Missing -> return ChangeOutcome.Failed(change.path, null)
                is Bytes -> files.write(file, scope, bytes.content, append = false)
                Gone -> delete(file)
            }
            dao.markRolledBack(change.revisionIds, direction == ChangeDirection.UNDO)
            ChangeOutcome.Done(change.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FileAccessException) {
            ChangeOutcome.Failed(change.path, e.message)
        } catch (e: IOException) {
            ChangeOutcome.Failed(change.path, e.message)
        }
    }

    private suspend fun delete(file: File) {
        val removed = withContext(Dispatchers.IO) { file.delete() || !file.exists() }
        if (!removed) throw IOException("Cannot delete ${file.path}")
    }

    private suspend fun ends(change: FileChange): Pair<RevisionEntity, RevisionEntity>? {
        val loaded = dao.byIds(listOf(change.firstId, change.lastId))
        val first = loaded.firstOrNull { it.id == change.firstId } ?: return null
        val last = loaded.firstOrNull { it.id == change.lastId } ?: return null
        return first to last
    }

    private suspend fun target(first: RevisionEntity, last: RevisionEntity, direction: ChangeDirection, created: Boolean): Target = withContext(Dispatchers.IO) {
        when {
            direction == ChangeDirection.UNDO && created -> Gone
            direction == ChangeDirection.UNDO -> blobs.read(first.id, RevisionBlobs.Side.BEFORE)?.let(::Bytes) ?: Missing
            else -> blobs.read(last.id, RevisionBlobs.Side.AFTER)?.let(::Bytes) ?: Missing
        }
    }

    private sealed interface Target

    private data object Missing : Target

    private data object Gone : Target

    private class Bytes(val content: ByteArray) : Target
}
