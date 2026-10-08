package dev.huecoder.md3agent.tools.system

import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.shell.ShellAccess
import dev.huecoder.md3agent.system.shell.ShellResult
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.requireString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

class RunCommandTool(private val access: ShellAccess) : Tool {
    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs = MAX_TIMEOUT_S * MS_PER_SECOND + TIMEOUT_GRACE_MS

    override val spec = ToolSpec(
        name = NAME,
        description = "Runs a shell command on the user's phone and returns its exit code, stdout and stderr. " +
            "Each call is a fresh non-interactive process: no TTY, no prompts, state does not carry over between calls. " +
            "Chain steps with && in one command. See the device access notes for which shell runs it and what it can reach.",
        parameters = objectSchema(required = listOf("command")) {
            stringProperty("command", "Shell command line")
            integerProperty("timeout_seconds", "How long to wait before killing the command, default $DEFAULT_TIMEOUT_S", 1, MAX_TIMEOUT_S.toInt())
        },
    )

    override fun promptNote(settings: ToolSettings): String = when (settings.access.shell) {
        ShellMode.SANDBOX -> "$NAME runs /system/bin/sh (toybox) inside this app's own sandbox, without root. " +
            "The working directory is the app workspace ${access.workspace.path}; other apps' data and most of shared storage are not reachable."
        ShellMode.TERMUX -> "$NAME runs bash inside Termux as the Termux user, starting in its home directory. " +
            "Termux packages are available and more can be installed with pkg install -y. No root."
        ShellMode.SHIZUKU -> "$NAME runs sh through Shizuku with adb shell privileges: pm, am, cmd, settings, dumpsys, input and similar tools work, but there is no root."
        ShellMode.ROOT -> "$NAME runs as root (uid 0) through su. Everything on the device is reachable, so double check destructive commands."
    }

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val command = arguments.requireString("command")
        val timeoutSeconds = (arguments.optionalInt("timeout_seconds") ?: DEFAULT_TIMEOUT_S.toInt()).toLong().coerceIn(1, MAX_TIMEOUT_S)
        val result = access.shell(settings.access.shell).run(command, timeoutSeconds * MS_PER_SECOND)
        return ToolOutcome(result.describe(timeoutSeconds), isError = result.exitCode == null)
    }

    private fun ShellResult.describe(timeoutSeconds: Long): String = buildString {
        if (timedOut) appendLine("Killed after $timeoutSeconds s without finishing") else appendLine("Exit code: ${exitCode ?: "unknown"}")
        if (stdout.isNotEmpty()) append("stdout:\n").appendLine(stdout.clipped())
        if (stderr.isNotEmpty()) append("stderr:\n").appendLine(stderr.clipped())
        if (stdout.isEmpty() && stderr.isEmpty()) appendLine("No output")
    }.trimEnd()

    private fun String.clipped(): String {
        if (length <= MAX_STREAM_CHARS) return trimEnd()
        val half = MAX_STREAM_CHARS / 2
        return take(half) + "\n… ${length - MAX_STREAM_CHARS} characters cut …\n" + takeLast(half).trimEnd()
    }

    companion object {
        const val NAME = "run_command"
        private const val DEFAULT_TIMEOUT_S = 60L
        private const val MAX_TIMEOUT_S = 300L
        private const val MS_PER_SECOND = 1_000L
        private const val TIMEOUT_GRACE_MS = 10_000L
        private const val MAX_STREAM_CHARS = 12_000
    }
}
