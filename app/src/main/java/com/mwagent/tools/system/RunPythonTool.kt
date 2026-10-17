package com.mwagent.tools.system

import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.control.ScriptPhone
import com.mwagent.system.python.PythonRuntime
import com.mwagent.tools.ContextualTool
import com.mwagent.tools.ToolContext
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.phone.PhoneScriptBridge
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class RunPythonTool(
    private val python: PythonRuntime,
    private val workspace: File,
    private val phone: ScriptPhone,
) : ContextualTool {
    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs = (MAX_TIMEOUT_S + TIMEOUT_GRACE_S) * MS_PER_SECOND

    override val spec = ToolSpec(
        name = NAME,
        description = "Runs a Python ${python.version} script inside the app and returns everything it printed. " +
            "Use it for calculations, parsing and converting data, and working with files. " +
            "Only the standard library is available, no pip packages. Each call starts with fresh globals, so save anything you need later to a file. " +
            "The working directory is the app workspace ${workspace.path}, files saved there can be shown to the user with send_files.",
        parameters = objectSchema(required = listOf(CODE)) {
            stringProperty(CODE, "Python source to run, print the results you need")
            integerProperty("timeout_seconds", "How long the script may run, default $DEFAULT_TIMEOUT_S", 1, MAX_TIMEOUT_S.toInt())
        },
    )

    override fun promptNote(settings: ToolSettings): String? = PHONE_NOTE.takeIf { settings.phoneUse.pythonControl }

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val code = arguments.requireString(CODE)
        val timeoutSeconds = (arguments.optionalInt("timeout_seconds")?.toLong() ?: DEFAULT_TIMEOUT_S).coerceIn(1, MAX_TIMEOUT_S)
        val bridge = if (settings.phoneUse.pythonControl) PhoneScriptBridge(phone, context.chatId) else null
        val result = python.run(code, workspace, timeoutSeconds, bridge)
        val output = result.output.ifBlank { "(no output)" }.let {
            if (it.length > MAX_OUTPUT_CHARS) it.take(MAX_OUTPUT_CHARS) + "\n…[output truncated at $MAX_OUTPUT_CHARS characters]" else it
        }
        val text = if (result.timedOut) "$output\nStopped after $timeoutSeconds s" else output
        return ToolOutcome(text, isError = result.failed || result.timedOut)
    }

    companion object {
        const val NAME = "run_python"
        private const val CODE = "code"
        private const val DEFAULT_TIMEOUT_S = 30L
        private const val MAX_TIMEOUT_S = 300L
        private const val TIMEOUT_GRACE_S = 10L
        private const val MS_PER_SECOND = 1_000L
        private const val MAX_OUTPUT_CHARS = 20_000
        private val PHONE_IMPORT = Regex("""(?:^|[;\s])(?:import\s+(?:[\w.]+\s*,\s*)*phone\b|from\s+phone\b)|(?:__import__|import_module)\(\s*["']phone["']""")

        fun usesPhone(code: String): Boolean = PHONE_IMPORT.containsMatchIn(code)

        fun usesPhone(arguments: JsonObject): Boolean = usesPhone((arguments[CODE] as? JsonPrimitive)?.content.orEmpty())

        private val PHONE_NOTE = listOf(
            "run_python can drive the phone through the built-in phone module (import phone). Use it for long or repetitive flows with loops and conditions, and give it a long timeout_seconds (up to $MAX_TIMEOUT_S). For a few single steps use phone_action directly.",
            "phone.screen(text=, id=, role=, exact=, detailed=) returns a Screen with .text, .app, .elements and the helpers .find(...) and .has(text). Each element has number, role, label, x, y, left, top, right, bottom, clickable, editable, scrollable, checked, focused, id. phone.find(...) returns matching elements and phone.exists(...) a bool.",
            "Actions take a target as a text (\"Wi-Fi\"), an element number or Element, or the keywords text=, id=, role=, exact=, index=: phone.tap, phone.long_press, phone.double_tap, phone.scroll(direction, target), phone.scroll_to(target, direction=None, max_swipes=12), phone.type_text(text, target=None, submit=False), phone.tap_at(x, y), phone.swipe(direction, distance=None, duration=None) or start=(x, y), end=(x, y), phone.drag([(x, y), ...]), phone.press(\"back\"), phone.open_app(\"Settings\"), phone.wait(seconds).",
            "phone.wait_for(text=, gone=, app=, idle=True, changed=True, timeout=10) waits for one condition and returns a falsy Result on timeout. Every other action returns a Result (ok, changed, text) and raises phone.PhoneError when it fails, so let errors stop the script unless you have a plan for them. Prefer text and id targets over element numbers, which go stale when the screen changes.",
            "Pass say=\"short first-person phrase\" to an action, or call phone.say(...), so the user can follow along. The same safety rules as for phone_action apply: no passwords, payments, purchases or deletions unless the user asked, and stop with an error if the screen is not what you expect.",
            "Save a script that worked as routines/<name>.py in the workspace with write_file. phone.routines() lists them and phone.routine(\"<name>\", key=value) runs one, the file reads its inputs from the params dict and can set result.",
        ).joinToString("\n")
    }
}
