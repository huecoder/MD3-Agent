package com.mwagent.data.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.mwagent.data.attachments.TextFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

enum class LibraryRoot { MINE, AGENT }

data class LibraryLocation(val root: LibraryRoot, val segments: List<String> = emptyList(), val fromChats: Boolean = false) {
    val isTop: Boolean get() = segments.isEmpty() && !fromChats

    fun child(name: String) = copy(segments = segments + name)
}

enum class EntryKind { FOLDER, FILE, CHAT_FILES }

data class LibraryEntry(
    val kind: EntryKind,
    val name: String,
    val file: ViewableFile?,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val isReadOnly: Boolean,
) {
    val key: String get() = file?.file?.path ?: "$kind:$name"
}

class InvalidFileNameException(name: String) : IllegalArgumentException("\"$name\" is not a valid file name")

class FileLibrary(
    private val resolver: ContentResolver,
    uploads: File,
    workspace: File,
    private val sentFiles: SentFiles,
    private val mimeTypeOf: (fileName: String) -> String = TextFormats::guessMimeType,
) {
    val uploads: File by lazy { uploads.apply { mkdirs() }.canonicalFile }
    private val workspace: File by lazy { workspace.apply { mkdirs() }.canonicalFile }
    private val hashes = ConcurrentHashMap<String, String>()

    suspend fun list(location: LibraryLocation): List<LibraryEntry> = withContext(Dispatchers.IO) {
        if (location.fromChats) return@withContext chatFiles(location.root)
        val folder = folderOf(location)
        val children = folder.listFiles().orEmpty()
            .filterNot { it.name.startsWith(HIDDEN_PREFIX) }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { it.toEntry() }
        val chatFolder = if (location.isTop && chatFiles(location.root).isNotEmpty()) {
            listOf(LibraryEntry(EntryKind.CHAT_FILES, "", null, 0, 0, isReadOnly = true))
        } else {
            emptyList()
        }
        chatFolder + children
    }

    suspend fun media(): List<ViewableFile> = withContext(Dispatchers.IO) {
        mediaCandidates()
            .sortedByDescending { it.modifiedAt }
            .distinctBy { contentHash(it.file.file) ?: it.file.file.path }
            .map { it.file }
    }

    suspend fun deleteMedia(targets: Collection<ViewableFile>) = withContext(Dispatchers.IO) {
        val paths = targets.map { it.file.path }.toSet()
        val hashes = targets.mapNotNull { contentHash(it.file) }.toSet()
        val failures = mediaCandidates()
            .map { it.file.file }
            .filter { file -> file.path in paths || contentHash(file) in hashes }
            .mapNotNull { file ->
                try {
                    file.deleteTree()
                    null
                } catch (e: IOException) {
                    e
                }
            }
        failures.firstOrNull()?.let { throw it }
    }

    suspend fun upload(uri: Uri, into: LibraryLocation): File = withContext(Dispatchers.IO) {
        val folder = folderOf(into)
        val name = displayName(uri) ?: uri.lastPathSegment ?: FALLBACK_NAME
        val target = freeName(folder, sanitized(name))
        val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the file")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        target
    }

    suspend fun createFolder(inside: LibraryLocation, name: String): File = withContext(Dispatchers.IO) {
        val folder = File(folderOf(inside), validName(name))
        if (folder.exists()) throw IOException("${folder.name} already exists")
        if (!folder.mkdirs()) throw IOException("Could not create ${folder.name}")
        folder
    }

    suspend fun rename(location: LibraryLocation, entry: LibraryEntry, name: String) = withContext(Dispatchers.IO) {
        val source = File(folderOf(location), entry.name)
        val target = File(source.parentFile, validName(name))
        if (target.exists()) throw IOException("${target.name} already exists")
        if (!source.renameTo(target)) throw IOException("Could not rename ${source.name}")
    }

    suspend fun delete(location: LibraryLocation, entries: Collection<LibraryEntry>) = withContext(Dispatchers.IO) {
        val folder = folderOf(location)
        val failures = entries.filterNot { it.isReadOnly }.mapNotNull { entry ->
            try {
                File(folder, entry.name).deleteTree()
                null
            } catch (e: IOException) {
                e
            }
        }
        failures.firstOrNull()?.let { throw it }
    }

    private suspend fun mediaCandidates(): List<MediaCandidate> {
        val folders = listOf(uploads, workspace).flatMap { root ->
            root.walkTopDown().maxDepth(MAX_MEDIA_DEPTH).onEnter { !it.name.startsWith(HIDDEN_PREFIX) || it == root }
                .filter { it.isFile && mimeTypeOf(it.name).startsWith(IMAGE_PREFIX) }
                .map { MediaCandidate(ViewableFile(it, it.name, mimeTypeOf(it.name)), it.lastModified()) }
                .toList()
        }
        val chats = sentFiles.all().filter { it.file.isImage && it.file.file.isFile }.map { MediaCandidate(it.file, it.sentAt) }
        return folders + chats
    }

    private suspend fun chatFiles(root: LibraryRoot): List<LibraryEntry> = sentFiles.all()
        .filter { it.sentByUser == (root == LibraryRoot.MINE) && it.file.file.isFile }
        .distinctBy { it.id }
        .map { sent -> LibraryEntry(EntryKind.FILE, sent.file.name, sent.file, sent.file.file.length(), sent.sentAt, isReadOnly = true) }

    private fun folderOf(location: LibraryLocation): File {
        val root = when (location.root) {
            LibraryRoot.MINE -> uploads
            LibraryRoot.AGENT -> workspace
        }
        val folder = location.segments.fold(root) { parent, segment -> File(parent, segment) }.canonicalFile
        if (folder != root && !folder.path.startsWith(root.path + File.separator)) throw IOException("${folder.path} is outside the library")
        return folder
    }

    private fun File.toEntry() = LibraryEntry(
        kind = if (isDirectory) EntryKind.FOLDER else EntryKind.FILE,
        name = name,
        file = if (isDirectory) null else ViewableFile(this, name, mimeTypeOf(name)),
        sizeBytes = if (isDirectory) 0 else length(),
        modifiedAt = lastModified(),
        isReadOnly = false,
    )

    private fun contentHash(file: File): String? {
        if (!file.isFile) return null
        val key = "${file.path}|${file.length()}|${file.lastModified()}"
        hashes[key]?.let { return it }
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        file.inputStream().use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.also { hashes[key] = it }
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun freeName(folder: File, name: String): File {
        val candidate = File(folder, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return generateSequence(2) { it + 1 }.map { File(folder, "$base ($it)$extension") }.first { !it.exists() }
    }

    private fun sanitized(name: String) = name.replace(File.separatorChar, '_').trim().ifEmpty { FALLBACK_NAME }

    private fun validName(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty() || clean == "." || clean == ".." || File.separatorChar in clean) throw InvalidFileNameException(name)
        return clean
    }

    private data class MediaCandidate(val file: ViewableFile, val modifiedAt: Long)

    private companion object {
        const val HIDDEN_PREFIX = "."
        const val IMAGE_PREFIX = "image/"
        const val MAX_MEDIA_DEPTH = 6
        const val FALLBACK_NAME = "file"
        const val HASH_ALGORITHM = "SHA-256"
        const val HASH_BUFFER_BYTES = 64 * 1024
    }
}
