package com.mwagent.system.files

import android.os.Build
import android.os.Environment
import com.mwagent.data.settings.FileScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class FileAccessException(message: String) : IllegalStateException(message)

data class FileEntry(val name: String, val isDirectory: Boolean, val sizeBytes: Long, val modifiedAt: Long)

class DeviceFiles(
    workspace: File,
    attachments: File,
    private val staging: File,
    uploads: File,
    private val sharedStorage: File = Environment.getExternalStorageDirectory(),
    private val root: RootFileStreams = RootFileStreams(),
) {
    val workspace: File by lazy { workspace.apply { mkdirs() }.canonicalFile }
    val attachments: File by lazy { attachments.canonicalFile }
    val uploads: File by lazy { uploads.apply { mkdirs() }.canonicalFile }
    val sharedStoragePath: String get() = sharedStorage.path

    fun hasSharedStorageAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    fun resolve(raw: String): File {
        val path = raw.trim().ifEmpty { "." }
        val file = File(path).let { if (it.isAbsolute) it else File(workspace, path) }
        return try {
            file.canonicalFile
        } catch (e: IOException) {
            file.absoluteFile
        }
    }

    suspend fun list(file: File, scope: FileScope): List<FileEntry> = withContext(Dispatchers.IO) {
        requireAllowed(file, scope, write = false)
        val children = file.listFiles()
        when {
            children != null -> children.map { FileEntry(it.name, it.isDirectory, if (it.isDirectory) 0 else it.length(), it.lastModified()) }
            scope == FileScope.ROOT -> listAsRoot(file)
            !file.exists() -> throw FileAccessException("${file.path} does not exist")
            !file.isDirectory -> throw FileAccessException("${file.path} is a file, not a folder")
            else -> throw FileAccessException("${file.path} cannot be opened without root")
        }
    }

    suspend fun <T> withLocalCopy(file: File, scope: FileScope, maxBytes: Long, block: suspend (File) -> T): T {
        requireAllowed(file, scope, write = false)
        val direct = withContext(Dispatchers.IO) { file.isFile && file.canRead() }
        if (direct) {
            if (file.length() > maxBytes) throw FileAccessException("${file.name} is larger than ${maxBytes / BYTES_PER_MB} MB")
            return block(file)
        }
        if (scope != FileScope.ROOT) throw FileAccessException("${file.path} does not exist or cannot be read")
        val folder = File(staging, UUID.randomUUID().toString()).apply { mkdirs() }
        return try {
            val copy = File(folder, file.name)
            root.copyOut(file.path, copy, maxBytes)
            block(copy)
        } finally {
            withContext(Dispatchers.IO) { folder.deleteRecursively() }
        }
    }

    suspend fun write(file: File, scope: FileScope, bytes: ByteArray, append: Boolean) {
        requireAllowed(file, scope, write = true)
        val written = withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                if (append) file.appendBytes(bytes) else file.writeBytes(bytes)
            }.isSuccess
        }
        if (written) return
        if (scope != FileScope.ROOT) throw FileAccessException("Cannot write ${file.path}")
        root.write(file.path, bytes, append)
    }

    private suspend fun listAsRoot(folder: File): List<FileEntry> {
        val output = root.run("cd -- ${RootFileStreams.quoted(folder.path)} && $STAT_EACH_ENTRY")
        return output.lineSequence().mapNotNull(::parseStatLine).toList()
    }

    private fun parseStatLine(line: String): FileEntry? {
        val parts = line.split(STAT_SEPARATOR, limit = STAT_FIELDS)
        if (parts.size != STAT_FIELDS) return null
        val (type, size, modified, name) = parts
        return FileEntry(
            name = name,
            isDirectory = type == STAT_DIRECTORY_TYPE,
            sizeBytes = size.toLongOrNull() ?: 0,
            modifiedAt = (modified.toLongOrNull() ?: 0) * MS_PER_SECOND,
        )
    }

    private fun requireAllowed(file: File, scope: FileScope, write: Boolean) {
        if (scope == FileScope.ROOT || file.isWithin(workspace) || (!write && (file.isWithin(attachments) || file.isWithin(uploads)))) return
        if (scope == FileScope.SHARED_STORAGE && file.isWithin(sharedStorage)) {
            if (!hasSharedStorageAccess()) throw FileAccessException("All files access is not granted. The user can turn it on in Device access settings")
            return
        }
        val action = if (write) "write" else "read"
        throw FileAccessException("${file.path} is outside the folders the assistant may $action")
    }

    private fun File.isWithin(folder: File): Boolean = path == folder.path || path.startsWith(folder.path + File.separator)

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024
        const val MS_PER_SECOND = 1_000L
        const val STAT_SEPARATOR = "|"
        const val STAT_FIELDS = 4
        const val STAT_DIRECTORY_TYPE = "directory"
        const val STAT_EACH_ENTRY = "for f in * .*; do " +
            "{ [ \"\$f\" = . ] || [ \"\$f\" = .. ] || [ ! -e \"\$f\" ]; } && continue; " +
            "stat -c '%F|%s|%Y|%n' -- \"\$f\"; done"
    }
}
