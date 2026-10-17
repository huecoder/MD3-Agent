package com.mwagent.data.files

import android.content.Context
import com.mwagent.data.attachments.TextFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest

object RemoteImages {
    private const val DIRECTORY = "remote-images"
    private const val FILE_URL_PREFIX = "file://"
    private const val PARTIAL_SUFFIX = ".part"
    private const val DEFAULT_NAME = "image"
    private const val DEFAULT_EXTENSION = "jpg"
    private const val MAX_NAME_CHARS = 60
    private const val MAX_BYTES = 40L * 1024 * 1024
    private const val FINGERPRINT_CHARS = 16
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif", "heic", "svg")
    private val UNSAFE_NAME = Regex("""[^\p{L}\p{N}._ -]+""")
    private val EXTENSIONS_BY_MIME = mapOf(
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "image/bmp" to "bmp",
        "image/avif" to "avif",
        "image/heic" to "heic",
        "image/svg+xml" to "svg",
    )

    fun directory(context: Context): File = File(context.cacheDir, DIRECTORY)

    fun viewable(
        directory: File,
        url: String,
        description: String,
        mimeTypeOf: (String) -> String = TextFormats::guessMimeType,
    ): ViewableFile {
        localFile(url)?.let { return ViewableFile(it, it.name, mimeTypeOf(it.name)) }
        val name = nameOf(url, description)
        return ViewableFile(File(directory, "${fingerprint(url)}.${TextFormats.extension(name)}"), name, mimeTypeOf(name), url)
    }

    suspend fun materialize(client: OkHttpClient, target: ViewableFile): ViewableFile = withContext(Dispatchers.IO) {
        val url = target.url ?: return@withContext target
        if (target.file.length() > 0) return@withContext target
        target.file.parentFile?.mkdirs()
        val partial = File(target.file.path + PARTIAL_SUFFIX)
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("The server answered ${response.code}")
                val body = response.body
                if (body.contentLength() > MAX_BYTES) throw IOException("The picture is too big")
                partial.outputStream().use { output -> body.byteStream().copyTo(output) }
                if (partial.length() > MAX_BYTES || partial.length() == 0L) throw IOException("The picture is empty or too big")
                if (!partial.renameTo(target.file)) throw IOException("Could not store the picture")
                corrected(target, response.header("Content-Type"))
            }
        } finally {
            partial.delete()
        }
    }

    internal fun nameOf(url: String, description: String): String {
        val lastSegment = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val decoded = runCatching { URLDecoder.decode(lastSegment, Charsets.UTF_8.name()) }.getOrDefault(lastSegment)
        val fromUrl = decoded.replace(UNSAFE_NAME, "_").trim('.', ' ', '_', '-')
        if (TextFormats.extension(fromUrl) in IMAGE_EXTENSIONS) return fromUrl.takeLast(MAX_NAME_CHARS * 2)
        val base = description.replace(UNSAFE_NAME, "_").trim('.', ' ', '_', '-').take(MAX_NAME_CHARS).ifEmpty { DEFAULT_NAME }
        return "$base.$DEFAULT_EXTENSION"
    }

    internal fun corrected(target: ViewableFile, contentType: String?): ViewableFile {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return target
        val extension = EXTENSIONS_BY_MIME[mime] ?: return target
        if (mime == target.mimeType) return target
        return target.copy(name = target.name.substringBeforeLast('.', target.name) + "." + extension, mimeType = mime)
    }

    private fun localFile(url: String): File? = when {
        url.startsWith(FILE_URL_PREFIX) -> File(runCatching { URLDecoder.decode(url.removePrefix(FILE_URL_PREFIX), Charsets.UTF_8.name()) }.getOrDefault(url.removePrefix(FILE_URL_PREFIX)))
        url.startsWith("/") && !url.startsWith("//") -> File(url)
        else -> null
    }

    private fun fingerprint(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(FINGERPRINT_CHARS)
}
