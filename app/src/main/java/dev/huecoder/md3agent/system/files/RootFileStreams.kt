package dev.huecoder.md3agent.system.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class RootFileStreams {
    suspend fun copyOut(source: String, target: File, maxBytes: Long) = runInterruptible(Dispatchers.IO) {
        val process = start("cat -- ${quoted(source)}")
        process.outputStream.close()
        try {
            target.outputStream().use { output -> copyCapped(process.inputStream, output, maxBytes) }
            requireSuccess(process)
        } finally {
            process.destroy()
        }
    }

    suspend fun write(path: String, bytes: ByteArray, append: Boolean) = runInterruptible(Dispatchers.IO) {
        val redirect = if (append) ">>" else ">"
        val parent = File(path).parent ?: "/"
        val process = start("mkdir -p -- ${quoted(parent)} && cat $redirect ${quoted(path)}")
        try {
            process.outputStream.use { it.write(bytes) }
            requireSuccess(process)
        } finally {
            process.destroy()
        }
    }

    suspend fun run(command: String): String = runInterruptible(Dispatchers.IO) {
        val process = start(command)
        process.outputStream.close()
        try {
            val output = process.inputStream.bufferedReader().readText()
            requireSuccess(process)
            output
        } finally {
            process.destroy()
        }
    }

    private fun start(command: String): Process = try {
        ProcessBuilder(SU, "-c", command).start()
    } catch (e: IOException) {
        throw FileAccessException("su is not available on this device: ${e.message}")
    }

    private fun requireSuccess(process: Process) {
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val error = process.errorStream.bufferedReader().readText().trim().take(ERROR_EXCERPT_CHARS)
            throw FileAccessException(error.ifEmpty { "Root command failed with exit code $exitCode" })
        }
    }

    private fun copyCapped(input: InputStream, output: OutputStream, maxBytes: Long) {
        val chunk = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw FileAccessException("The file is larger than ${maxBytes / BYTES_PER_MB} MB")
            output.write(chunk, 0, read)
        }
    }

    companion object {
        private const val SU = "su"
        private const val BUFFER_BYTES = 64 * 1024
        private const val BYTES_PER_MB = 1024L * 1024
        private const val ERROR_EXCERPT_CHARS = 300

        fun quoted(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
