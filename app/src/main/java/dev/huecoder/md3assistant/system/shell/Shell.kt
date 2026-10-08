package dev.huecoder.md3assistant.system.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException

interface Shell {
    suspend fun run(command: String, timeoutMs: Long): ShellResult
}

enum class AccessStatus { READY, UNKNOWN, NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, UNAVAILABLE }

class SandboxShell(private val workspace: File, private val tempDir: File) : Shell {
    override suspend fun run(command: String, timeoutMs: Long): ShellResult = runInterruptible(Dispatchers.IO) {
        workspace.mkdirs()
        ProcessRunner.run(
            listOf(SYSTEM_SH, "-c", command),
            timeoutMs,
            workdir = workspace,
            environment = mapOf("HOME" to workspace.path, "TMPDIR" to tempDir.path),
        )
    }
}

class RootShell : Shell {
    override suspend fun run(command: String, timeoutMs: Long): ShellResult = runInterruptible(Dispatchers.IO) {
        try {
            ProcessRunner.run(listOf(SU, "-c", command), timeoutMs)
        } catch (e: IOException) {
            throw IllegalStateException("su is not available on this device: ${e.message}")
        }
    }

    suspend fun status(): AccessStatus = try {
        val probe = run("id -u", PROBE_TIMEOUT_MS)
        if (probe.exitCode == 0 && probe.stdout.trim() == ROOT_UID) AccessStatus.READY else AccessStatus.NEEDS_PERMISSION
    } catch (e: IllegalStateException) {
        AccessStatus.UNAVAILABLE
    }

    private companion object {
        const val SU = "su"
        const val ROOT_UID = "0"
        const val PROBE_TIMEOUT_MS = 20_000L
    }
}

internal const val SYSTEM_SH = "/system/bin/sh"
