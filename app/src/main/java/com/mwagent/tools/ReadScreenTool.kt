package com.mwagent.tools

import com.mwagent.assistant.ScreenContextStore
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.TimeUnit

class ReadScreenTool(
    private val screen: ScreenContextStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads the text of the screen the user had open when the assistant was invoked. " +
            "Use it when the question refers to \"this\", \"here\" or \"on the screen\".",
        parameters = objectSchema(required = emptyList()) {},
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val snapshot = screen.latest.value
            ?: return ToolOutcome.error("No screen captured: the assistant was not opened over another app")
        if (snapshot.text.isBlank()) {
            return ToolOutcome.error("The app ${snapshot.packageName ?: "on screen"} provided no text (it may be protected from reading)")
        }
        val ageSeconds = TimeUnit.MILLISECONDS.toSeconds(clock() - snapshot.capturedAt)
        return ToolOutcome(
            buildString {
                appendLine("App: ${snapshot.packageName ?: "unknown"}")
                appendLine("Captured $ageSeconds s ago")
                appendLine()
                append(snapshot.text)
            },
        )
    }

    companion object {
        const val NAME = "read_screen"
    }
}
