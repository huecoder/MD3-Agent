package dev.huecoder.md3agent.data.attachments

import android.content.ContentResolver
import dev.huecoder.md3agent.data.files.ViewableFile
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.max

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val forModel: Boolean = true,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")

    val isText: Boolean get() = TextFormats.isText(mimeType, name)

    companion object {
        private val listSerializer = ListSerializer(serializer())
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(attachments: List<Attachment>): String? = attachments.takeIf { it.isNotEmpty() }?.let { json.encodeToString(listSerializer, it) }

        fun decode(raw: String?): List<Attachment> =
            raw?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
    }
}

sealed interface AttachmentContent {
    data class Image(val dataUrl: String) : AttachmentContent
    data class Text(val name: String, val text: String) : AttachmentContent
    data class Stored(val name: String, val mimeType: String, val sizeBytes: Long, val path: String) : AttachmentContent
}

class AttachmentTooLargeException(val limitMb: Int) : IOException("Attachment is larger than $limitMb MB")

class AttachmentStore(private val context: Context) {
    val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    suspend fun import(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(resolver, uri) ?: uri.lastPathSegment ?: FALLBACK_NAME
        val mimeType = resolver.getType(uri) ?: TextFormats.guessMimeType(name)
        if (mimeType.startsWith("image/")) {
            storeImageForModel(name) { ImageDecoder.createSource(resolver, uri) }
        } else {
            val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the file")
            storeRaw(name, mimeType, forModel = true) { target -> input.use { copyCapped(it, target) } }
        }
    }

    suspend fun importLocal(source: File, forModel: Boolean): Attachment = withContext(Dispatchers.IO) {
        val name = source.name
        val mimeType = TextFormats.guessMimeType(name)
        if (forModel && mimeType.startsWith("image/")) {
            storeImageForModel(name) { ImageDecoder.createSource(source) }
        } else {
            storeRaw(name, mimeType, forModel) { target -> source.inputStream().use { copyCapped(it, target) } }
        }
    }

    suspend fun importBytes(name: String, bytes: ByteArray, forModel: Boolean): Attachment = withContext(Dispatchers.IO) {
        val mimeType = TextFormats.guessMimeType(name)
        if (forModel && mimeType.startsWith("image/")) {
            storeImageForModel(name) { ImageDecoder.createSource(ByteBuffer.wrap(bytes)) }
        } else {
            storeRaw(name, mimeType, forModel) { target -> target.writeBytes(bytes) }
        }
    }

    fun file(attachment: Attachment): File = File(directory, attachment.id)

    fun viewable(attachment: Attachment) = ViewableFile(file(attachment), attachment.name, attachment.mimeType)

    suspend fun contentFor(attachment: Attachment): AttachmentContent? = withContext(Dispatchers.IO) {
        val file = file(attachment)
        when {
            !attachment.forModel || !file.exists() -> null
            attachment.isImage -> AttachmentContent.Image("data:${attachment.mimeType};base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            attachment.isText && file.length() <= MAX_INLINE_TEXT_BYTES -> AttachmentContent.Text(attachment.name, file.readText())
            else -> AttachmentContent.Stored(attachment.name, attachment.mimeType, file.length(), file.absolutePath)
        }
    }

    suspend fun delete(attachments: List<Attachment>) = withContext(Dispatchers.IO) {
        attachments.forEach { file(it).delete() }
    }

    suspend fun exportToDownloads(target: ViewableFile) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val details = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, target.name)
            put(MediaStore.Downloads.MIME_TYPE, target.mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOADS_FOLDER)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, details) ?: throw IOException("Could not create the file in Downloads")
        try {
            resolver.openOutputStream(destination)?.use { output -> target.file.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Could not write to Downloads")
            resolver.update(destination, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(destination, null, null)
            throw e
        }
    }

    private fun storeImageForModel(name: String, source: () -> ImageDecoder.Source): Attachment {
        val id = newId(JPEG_EXTENSION)
        val target = File(directory, id)
        val bitmap = ImageDecoder.decodeBitmap(source()) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_IMAGE_SIDE_PX) {
                val scale = MAX_IMAGE_SIDE_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        return Attachment(id, name, STORED_IMAGE_MIME, target.length())
    }

    private fun storeRaw(name: String, mimeType: String, forModel: Boolean, write: (File) -> Unit): Attachment {
        val target = File(directory, newId(TextFormats.extension(name)))
        try {
            write(target)
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        return Attachment(target.name, name, mimeType, target.length(), forModel)
    }

    private fun copyCapped(input: InputStream, target: File) {
        target.outputStream().use { output ->
            val chunk = ByteArray(COPY_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > MAX_FILE_BYTES) throw AttachmentTooLargeException((MAX_FILE_BYTES / BYTES_PER_MB).toInt())
                output.write(chunk, 0, read)
            }
        }
    }

    private fun newId(extension: String): String {
        val base = UUID.randomUUID().toString()
        return if (extension.matches(SAFE_EXTENSION)) "$base.$extension" else base
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private companion object {
        const val DIRECTORY = "attachments"
        const val DOWNLOADS_FOLDER = "MD3 Agent"
        const val FALLBACK_NAME = "file"
        const val STORED_IMAGE_MIME = "image/jpeg"
        const val JPEG_EXTENSION = "jpg"
        const val MAX_IMAGE_SIDE_PX = 1600
        const val JPEG_QUALITY = 85
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val BYTES_PER_MB = 1024L * 1024
        const val MAX_FILE_BYTES = 64 * BYTES_PER_MB
        const val MAX_INLINE_TEXT_BYTES = 256 * 1024L
        val SAFE_EXTENSION = Regex("[a-z0-9]{1,8}")
    }
}

object TextFormats {
    private val textMimeTypes = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-yaml",
        "application/yaml", "application/toml", "application/x-sh", "application/sql", "application/csv",
    )
    private val textExtensions = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "xml", "yaml", "yml", "toml", "ini", "conf", "cfg", "log",
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "go", "rs", "c", "h", "cpp", "hpp", "cs", "swift",
        "rb", "php", "sh", "bash", "zsh", "sql", "html", "htm", "css", "scss", "gradle", "properties", "env",
    )

    fun isText(mimeType: String, name: String): Boolean =
        mimeType.startsWith("text/") || mimeType in textMimeTypes || extension(name) in textExtensions

    fun guessMimeType(name: String): String {
        val extension = extension(name)
        return when {
            extension in textExtensions -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: GENERIC_MIME
        }
    }

    fun extension(name: String) = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    private const val GENERIC_MIME = "application/octet-stream"
}
