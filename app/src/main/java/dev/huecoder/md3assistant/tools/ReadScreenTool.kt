package dev.huecoder.md3assistant.tools

import dev.huecoder.md3assistant.assistant.ScreenContextStore
import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolSpec
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.TimeUnit

class ReadScreenTool(
    private val screen: ScreenContextStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Читает текст с экрана, который был открыт у пользователя в момент вызова ассистента. " +
            "Используй, когда вопрос про «это», «тут», «на экране».",
        parameters = objectSchema(required = emptyList()) {},
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val snapshot = screen.latest.value
            ?: return ToolOutcome.error("Экран не захвачен: ассистент открыт не поверх другого приложения")
        if (snapshot.text.isBlank()) {
            return ToolOutcome.error("Приложение ${snapshot.packageName ?: "на экране"} не отдало текст (возможно, защищено от чтения)")
        }
        val ageSeconds = TimeUnit.MILLISECONDS.toSeconds(clock() - snapshot.capturedAt)
        return ToolOutcome(
            buildString {
                appendLine("Приложение: ${snapshot.packageName ?: "неизвестно"}")
                appendLine("Снято $ageSeconds с назад")
                appendLine()
                append(snapshot.text)
            },
        )
    }

    companion object {
        const val NAME = "read_screen"
    }
}
