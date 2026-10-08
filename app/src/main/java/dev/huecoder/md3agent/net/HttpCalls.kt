package dev.huecoder.md3agent.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpStatusException(val code: Int, bodyExcerpt: String) : IOException("HTTP $code: $bodyExcerpt")

suspend fun OkHttpClient.fetchText(request: Request, maxChars: Int = DEFAULT_MAX_CHARS): String =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val outcome = runCatching { response.use(::readCapped) }
                    outcome.fold(
                        onSuccess = { text ->
                            if (!response.isSuccessful) {
                                continuation.resumeWithException(HttpStatusException(response.code, text.take(ERROR_EXCERPT_CHARS)))
                            } else {
                                continuation.resume(text.take(maxChars))
                            }
                        },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }
            },
        )
    }

private fun readCapped(response: Response): String {
    val source = response.body.source()
    source.request(READ_LIMIT_BYTES)
    val buffer = source.buffer
    val bytes = minOf(buffer.size, READ_LIMIT_BYTES)
    return buffer.readString(bytes, response.body.contentType()?.charset() ?: Charsets.UTF_8)
}

private const val DEFAULT_MAX_CHARS = 200_000
private const val READ_LIMIT_BYTES = 2L * 1024 * 1024
private const val ERROR_EXCERPT_CHARS = 200

suspend fun OkHttpClient.fetchToFile(request: Request, target: File, maxBytes: Long = DEFAULT_MAX_FILE_BYTES): File =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val outcome = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw HttpStatusException(it.code, readCapped(it).take(ERROR_EXCERPT_CHARS))
                            val length = it.body.contentLength()
                            if (length > maxBytes) throw IOException("Response is larger than $maxBytes bytes")
                            target.sink().buffer().use { sink -> sink.writeAll(it.body.source()) }
                            target
                        }
                    }
                    outcome.fold(continuation::resume) { continuation.resumeWithException(it) }
                }
            },
        )
    }

private const val DEFAULT_MAX_FILE_BYTES = 32L * 1024 * 1024
