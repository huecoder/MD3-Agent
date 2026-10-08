package dev.huecoder.md3assistant.system.shell

import android.os.Bundle
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ShellResult(val exitCode: Int?, val stdout: String, val stderr: String, val timedOut: Boolean = false) {
    fun toBundle() = Bundle().apply {
        exitCode?.let { putInt(KEY_EXIT_CODE, it) }
        putString(KEY_STDOUT, stdout)
        putString(KEY_STDERR, stderr)
        putBoolean(KEY_TIMED_OUT, timedOut)
    }

    companion object {
        private const val KEY_EXIT_CODE = "exitCode"
        private const val KEY_STDOUT = "stdout"
        private const val KEY_STDERR = "stderr"
        private const val KEY_TIMED_OUT = "timedOut"

        fun fromBundle(bundle: Bundle) = ShellResult(
            exitCode = if (bundle.containsKey(KEY_EXIT_CODE)) bundle.getInt(KEY_EXIT_CODE) else null,
            stdout = bundle.getString(KEY_STDOUT).orEmpty(),
            stderr = bundle.getString(KEY_STDERR).orEmpty(),
            timedOut = bundle.getBoolean(KEY_TIMED_OUT),
        )
    }
}

object ProcessRunner {
    private const val MAX_CAPTURE_BYTES = 256 * 1024
    private const val DRAIN_WAIT_MS = 2_000L

    fun run(command: List<String>, timeoutMs: Long, workdir: File? = null, environment: Map<String, String> = emptyMap()): ShellResult {
        val process = ProcessBuilder(command)
            .apply {
                workdir?.let(::directory)
                environment().putAll(environment)
            }
            .start()
        process.outputStream.close()
        val stdout = StreamCollector(process.inputStream).apply { start() }
        val stderr = StreamCollector(process.errorStream).apply { start() }
        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            throw e
        }
        if (!finished) process.destroyForcibly()
        stdout.join(DRAIN_WAIT_MS)
        stderr.join(DRAIN_WAIT_MS)
        return ShellResult(
            exitCode = if (finished) process.exitValue() else null,
            stdout = stdout.text(),
            stderr = stderr.text(),
            timedOut = !finished,
        )
    }

    private class StreamCollector(private val stream: InputStream) : Thread() {
        private val captured = ByteArrayOutputStream()

        init {
            isDaemon = true
        }

        override fun run() {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            runCatching {
                stream.use {
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        synchronized(captured) {
                            val room = MAX_CAPTURE_BYTES - captured.size()
                            if (room > 0) captured.write(buffer, 0, minOf(read, room))
                        }
                    }
                }
            }
        }

        fun text(): String = synchronized(captured) { captured.toString(Charsets.UTF_8.name()) }
    }
}
