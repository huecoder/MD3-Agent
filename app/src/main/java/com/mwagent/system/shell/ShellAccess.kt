package com.mwagent.system.shell

import android.content.Context
import com.mwagent.data.settings.ShellMode
import java.io.File

class ShellAccess(context: Context) {
    val workspace = File(context.filesDir, WORKSPACE_DIR)
    val termux = TermuxShell(context)
    val shizuku = ShizukuShell(context)
    val root = RootShell()
    private val sandbox = SandboxShell(workspace, context.cacheDir)

    fun shell(mode: ShellMode): Shell = when (mode) {
        ShellMode.SANDBOX -> sandbox
        ShellMode.TERMUX -> termux
        ShellMode.SHIZUKU -> shizuku
        ShellMode.ROOT -> root
    }

    suspend fun status(mode: ShellMode, probeRoot: Boolean): AccessStatus = when (mode) {
        ShellMode.SANDBOX -> AccessStatus.READY
        ShellMode.TERMUX -> termux.status()
        ShellMode.SHIZUKU -> shizuku.status()
        ShellMode.ROOT -> if (probeRoot) root.status() else AccessStatus.UNKNOWN
    }

    private companion object {
        const val WORKSPACE_DIR = "workspace"
    }
}
