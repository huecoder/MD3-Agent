package dev.huecoder.md3agent.tools

import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class ButtonAction(val wireName: String) { OPEN_URL("open_url"), COPY("copy"), SEND("send") }

data class ReplyButton(val label: String, val action: ButtonAction, val value: String)

data class ReplyUi(val suggestions: List<String>, val buttons: List<ReplyButton>) {
    val isEmpty: Boolean get() = suggestions.isEmpty() && buttons.isEmpty()

    companion object {
        private const val MAX_SUGGESTIONS = 4
        private const val MAX_BUTTONS = 4
        private const val MAX_LABEL_CHARS = 60
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(arguments: JsonObject): ReplyUi = ReplyUi(
            suggestions = arguments.optionalArray("suggestions")
                .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_LABEL_CHARS) }
                .distinct()
                .take(MAX_SUGGESTIONS),
            buttons = arguments.optionalArray("buttons")
                .mapNotNull { element -> (element as? JsonObject)?.let(::parseButton) }
                .take(MAX_BUTTONS),
        )

        fun parse(rawArguments: String): ReplyUi =
            runCatching { parse(json.parseToJsonElement(rawArguments).jsonObject) }.getOrDefault(ReplyUi(emptyList(), emptyList()))

        private fun parseButton(button: JsonObject): ReplyButton? {
            val label = button.optionalString("label")?.take(MAX_LABEL_CHARS) ?: return null
            val action = ButtonAction.entries.firstOrNull { it.wireName == button.optionalString("action") } ?: return null
            val value = button.optionalString("value") ?: return null
            if (action == ButtonAction.OPEN_URL && !value.startsWith("https://") && !value.startsWith("http://")) return null
            return ReplyButton(label, action, value)
        }
    }
}

class ShowUiTool : Tool {
    override val endsTurn: Boolean = true

    override val spec = ToolSpec(
        name = NAME,
        description = "Shows the user quick reply options (chips) and/or buttons below your answer. " +
            "Call it at the very end, after the answer text, when there are obvious next steps. " +
            "Tapping a chip sends its text as the user's message.",
        parameters = objectSchema(required = emptyList()) {
            putJsonObject("suggestions") {
                put("type", "array")
                put("description", "Up to 4 short replies written as the user")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("buttons") {
                put("type", "array")
                put("description", "Up to 4 action buttons")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        stringProperty("label", "Button text")
                        putJsonObject("action") {
                            put("type", "string")
                            putJsonArray("enum") { ButtonAction.entries.forEach { add(JsonPrimitive(it.wireName)) } }
                            put("description", "open_url opens a link, copy copies value, send sends value as a message")
                        }
                        stringProperty("value", "URL, text to copy, or message")
                    }
                    putJsonArray("required") { listOf("label", "action", "value").forEach { add(JsonPrimitive(it)) } }
                }
            }
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val ui = ReplyUi.parse(arguments)
        return if (ui.isEmpty) ToolOutcome.error("Nothing to show: no valid suggestions or buttons") else ToolOutcome("Shown to the user")
    }

    companion object {
        const val NAME = "show_ui"
    }
}
