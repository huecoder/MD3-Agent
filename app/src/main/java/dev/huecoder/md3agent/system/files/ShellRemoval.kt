package dev.huecoder.md3agent.system.files

import dev.huecoder.md3agent.data.files.RemovalFallback
import dev.huecoder.md3agent.system.shell.Shell
import kotlinx.coroutines.TimeoutCancellationException
import java.io.File

class ShellRemoval(
    private val appFiles: File,
    private val shellFor: suspend () -> Shell?,
) : RemovalFallback {
    override suspend fun remove(file: File): Boolean {
        if (!file.isInside(appFiles)) return false
        val shell = shellFor() ?: return false
        val finished = try {
            shell.run("rm -rf -- ${RootFileStreams.quoted(file.path)}", TIMEOUT_MS).exitCode == 0
        } catch (e: IllegalStateException) {
            false
        } catch (e: TimeoutCancellationException) {
            false
        }
        return finished && !file.exists()
    }

    private fun File.isInside(folder: File): Boolean {
        val base = folder.canonicalFile.path
        return canonicalFile.path.startsWith(base + File.separator)
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
