package dev.huecoder.md3assistant.data.attachments

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

@Serializable
data class Attachment(val id: String, val name: String, val mimeType: String, val sizeBytes: Long) {
    val isImage: Boolean get() = mimeType.startsWith("image/")

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
}

class UnsupportedAttachmentException(message: String) : IOException(message)

class AttachmentStore(private val context: Context) {
    private val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    suspend fun import(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(resolver, uri) ?: uri.lastPathSegment ?: FALLBACK_NAME
        val mimeType = resolver.getType(uri) ?: TextFormats.guessMimeType(name)
        val id = UUID.randomUUID().toString()
        val target = File(directory, id)
        when {
            mimeType.startsWith("image/") -> importImage(uri, target)
            TextFormats.isText(mimeType, name) -> importText(resolver, uri, target)
            else -> throw UnsupportedAttachmentException("«$name»: пока поддерживаются только картинки и текстовые файлы")
        }
        val storedMime = if (mimeType.startsWith("image/")) STORED_IMAGE_MIME else mimeType
        Attachment(id, name, storedMime, target.length())
    }

    fun file(attachment: Attachment): File = File(directory, attachment.id)

    suspend fun contentFor(attachment: Attachment): AttachmentContent? = withContext(Dispatchers.IO) {
        val file = file(attachment)
        if (!file.exists()) return@withContext null
        if (attachment.isImage) {
            AttachmentContent.Image("data:${attachment.mimeType};base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        } else {
            AttachmentContent.Text(attachment.name, file.readText())
        }
    }

    suspend fun delete(attachments: List<Attachment>) = withContext(Dispatchers.IO) {
        attachments.forEach { file(it).delete() }
    }

    private fun importImage(uri: Uri, target: File) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_IMAGE_SIDE_PX) {
                val scale = MAX_IMAGE_SIDE_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
    }

    private fun importText(resolver: ContentResolver, uri: Uri, target: File) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
        input.use { stream ->
            val bytes = readAtMost(stream, MAX_TEXT_BYTES + 1)
            if (bytes.size > MAX_TEXT_BYTES) {
                throw UnsupportedAttachmentException("Файл больше ${MAX_TEXT_BYTES / BYTES_PER_KB} КБ, это слишком много для контекста модели")
            }
            target.writeBytes(bytes)
        }
    }

    private fun readAtMost(stream: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(BYTES_PER_KB)
        while (out.size() < limit) {
            val read = stream.read(chunk, 0, minOf(chunk.size, limit - out.size()))
            if (read == -1) break
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private companion object {
        const val DIRECTORY = "attachments"
        const val FALLBACK_NAME = "файл"
        const val STORED_IMAGE_MIME = "image/jpeg"
        const val MAX_IMAGE_SIDE_PX = 1600
        const val JPEG_QUALITY = 85
        const val BYTES_PER_KB = 1024
        const val MAX_TEXT_BYTES = 256 * BYTES_PER_KB
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

    fun guessMimeType(name: String): String = if (extension(name) in textExtensions) "text/plain" else "application/octet-stream"

    private fun extension(name: String) = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
}
