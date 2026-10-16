package dev.huecoder.md3agent.data.files

import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

fun interface RemovalFallback {
    suspend fun remove(file: File): Boolean

    companion object {
        val None = RemovalFallback { false }

        fun firstOf(vararg fallbacks: RemovalFallback) = RemovalFallback { file -> fallbacks.any { it.remove(file) } }
    }
}

enum class DeleteReason { PERMISSION_DENIED, NOT_EMPTY, OTHER }

class DeleteFailedException(val path: String, val reason: DeleteReason, val detail: String?, cause: IOException) :
    IOException("Could not delete $path: ${detail ?: reason}", cause)

internal fun File.deleteTree(remove: (Path) -> Unit = { Files.deleteIfExists(it) }) {
    val path = toPath()
    var firstFailure: IOException? = null
    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        unlockForDeletion()
        for (child in listFiles().orEmpty()) {
            try {
                child.deleteTree(remove)
            } catch (e: IOException) {
                firstFailure = firstFailure ?: e
            }
        }
    }
    firstFailure?.let { throw it }
    remove(path)
}

internal fun File.describeFailure(failure: IOException): DeleteFailedException {
    val failed = (failure as? FileSystemException)?.file?.let(::File)?.takeIf { it.path.startsWith(path) } ?: this
    val shown = failed.relativeTo(parentFile ?: this).path
    val reason = when (failure) {
        is AccessDeniedException -> DeleteReason.PERMISSION_DENIED
        is DirectoryNotEmptyException -> DeleteReason.NOT_EMPTY
        else -> DeleteReason.OTHER
    }
    val detail = if (failure is FileSystemException) failure.reason else failure.message
    return DeleteFailedException(shown, reason, detail, failure)
}

private fun File.unlockForDeletion() {
    setReadable(true)
    setWritable(true)
    setExecutable(true)
}
