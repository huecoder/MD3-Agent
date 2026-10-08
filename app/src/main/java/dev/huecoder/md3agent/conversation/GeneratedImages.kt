package dev.huecoder.md3agent.conversation

import android.util.Base64
import android.util.Log
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.net.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

class GeneratedImages(private val attachments: AttachmentStore, private val httpClients: HttpClientProvider) {
    suspend fun store(urls: List<String>): List<Attachment> = urls.distinct().mapIndexedNotNull { index, url ->
        try {
            val (bytes, extension) = fetch(url)
            attachments.importBytes("image-${index + 1}.$extension", bytes, forModel = true)
        } catch (e: IOException) {
            Log.w(TAG, "Generated image could not be saved", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Generated image is not valid base64", e)
            null
        }
    }

    private suspend fun fetch(url: String): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        if (url.startsWith(DATA_PREFIX)) {
            val header = url.substringBefore(',')
            val mime = header.removePrefix(DATA_PREFIX).substringBefore(';')
            Base64.decode(url.substringAfter(','), Base64.DEFAULT) to extensionOf(mime)
        } else {
            httpClients.current().newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val mime = response.header("Content-Type").orEmpty().substringBefore(';')
                response.body.bytes() to extensionOf(mime)
            }
        }
    }

    private fun extensionOf(mime: String) = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }

    private companion object {
        const val TAG = "GeneratedImages"
        const val DATA_PREFIX = "data:"
    }
}
