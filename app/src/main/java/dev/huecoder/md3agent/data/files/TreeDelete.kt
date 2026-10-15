package dev.huecoder.md3agent.data.files

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

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

private fun File.unlockForDeletion() {
    setReadable(true)
    setWritable(true)
    setExecutable(true)
}
