package dev.huecoder.md3agent.data.changes

import dev.huecoder.md3agent.data.settings.FileScope
import dev.huecoder.md3agent.system.files.DeviceFiles
import dev.huecoder.md3agent.system.files.FileAccessException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

sealed interface Snapshot {
    data object Absent : Snapshot

    class Text(val bytes: ByteArray) : Snapshot {
        val hash: String get() = FileSnapshots.hash(bytes)
    }

    data object Opaque : Snapshot
}

class FileSnapshots(private val files: DeviceFiles) {
    suspend fun capture(file: File, scope: FileScope): Snapshot = withContext(Dispatchers.IO) {
        when {
            !file.exists() -> if (nearestExisting(file)?.canRead() == true) Snapshot.Absent else Snapshot.Opaque
            file.isDirectory -> Snapshot.Opaque
            else -> readText(file, scope)
        }
    }

    private suspend fun readText(file: File, scope: FileScope): Snapshot = try {
        val bytes = files.withLocalCopy(file, scope, MAX_SNAPSHOT_BYTES) { copy -> copy.readBytes() }
        if (looksBinary(bytes)) Snapshot.Opaque else Snapshot.Text(bytes)
    } catch (e: FileAccessException) {
        Snapshot.Opaque
    } catch (e: IOException) {
        Snapshot.Opaque
    }

    private fun nearestExisting(file: File): File? = generateSequence(file.parentFile) { it.parentFile }.firstOrNull { it.exists() }

    private fun looksBinary(bytes: ByteArray): Boolean = bytes.take(BINARY_SNIFF_BYTES).any { it == NUL }

    companion object {
        const val MAX_SNAPSHOT_BYTES = 1024L * 1024
        private const val BINARY_SNIFF_BYTES = 8 * 1024
        private const val NUL: Byte = 0

        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
