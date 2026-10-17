package dev.huecoder.md3agent.tools

import dev.huecoder.md3agent.assistant.ScreenContextStore
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.TimeUnit

class ReadScreenTool(
    private val screen: ScreenContextStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads the screen the user had open when the assistant was invoked: the app and its text and elements. " +
            "Use it when the question refers to \"this\", \"here\" or \"on the screen\".",
        parameters = objectSchema(required = emptyList()) {},
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val snapshot = screen.latest.value ?: return ToolOutcome.error(NOTHING_CAPTURED)
        if (snapshot.text.isBlank() && snapshot.elements == null) {
            return ToolOutcome.error("The app ${snapshot.packageName ?: "on screen"} provided no text (it may be protected from reading)")
        }
        val ageSeconds = TimeUnit.MILLISECONDS.toSeconds(clock() - snapshot.capturedAt)
        return ToolOutcome(
            buildString {
                appendLine("App: ${snapshot.packageName ?: "unknown"}")
                appendLine("Captured $ageSeconds s ago")
                if (snapshot.text.isNotBlank()) {
                    appendLine()
                    append(snapshot.text)
                }
                snapshot.elements?.let {
                    appendLine()
                    appendLine("Screen elements:")
                    append(it)
                }
            },
        )
    }

    companion object {
        const val NAME = "read_screen"
        private const val NOTHING_CAPTURED = "No screen captured: the assistant was not opened over another app, or the user turned off Use text from screen in the system assistant settings"
    }
}
