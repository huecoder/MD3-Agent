package dev.huecoder.md3assistant.tools.system

import dev.huecoder.md3assistant.data.settings.ToolPermission
import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolSpec
import dev.huecoder.md3assistant.system.python.PythonRuntime
import dev.huecoder.md3assistant.tools.Tool
import dev.huecoder.md3assistant.tools.ToolOutcome
import dev.huecoder.md3assistant.tools.integerProperty
import dev.huecoder.md3assistant.tools.objectSchema
import dev.huecoder.md3assistant.tools.optionalInt
import dev.huecoder.md3assistant.tools.requireString
import dev.huecoder.md3assistant.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import java.io.File

class RunPythonTool(private val python: PythonRuntime, private val workspace: File) : Tool {
    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs = (MAX_TIMEOUT_S + TIMEOUT_GRACE_S) * MS_PER_SECOND

    override val spec = ToolSpec(
        name = NAME,
        description = "Runs a Python ${python.version} script inside the app and returns everything it printed. " +
            "Use it for calculations, parsing and converting data, and working with files. " +
            "Only the standard library is available, no pip packages. Each call starts with fresh globals, so save anything you need later to a file. " +
            "The working directory is the app workspace ${workspace.path}, files saved there can be shown to the user with send_files.",
        parameters = objectSchema(required = listOf("code")) {
            stringProperty("code", "Python source to run, print the results you need")
            integerProperty("timeout_seconds", "How long the script may run, default $DEFAULT_TIMEOUT_S", 1, MAX_TIMEOUT_S.toInt())
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val code = arguments.requireString("code")
        val timeoutSeconds = (arguments.optionalInt("timeout_seconds")?.toLong() ?: DEFAULT_TIMEOUT_S).coerceIn(1, MAX_TIMEOUT_S)
        val result = python.run(code, workspace, timeoutSeconds)
        val output = result.output.ifBlank { "(no output)" }.let {
            if (it.length > MAX_OUTPUT_CHARS) it.take(MAX_OUTPUT_CHARS) + "\n…[output truncated at $MAX_OUTPUT_CHARS characters]" else it
        }
        val text = if (result.timedOut) "$output\nStopped after $timeoutSeconds s" else output
        return ToolOutcome(text, isError = result.failed || result.timedOut)
    }

    companion object {
        const val NAME = "run_python"
        private const val DEFAULT_TIMEOUT_S = 30L
        private const val MAX_TIMEOUT_S = 120L
        private const val TIMEOUT_GRACE_S = 10L
        private const val MS_PER_SECOND = 1_000L
        private const val MAX_OUTPUT_CHARS = 20_000
    }
}
