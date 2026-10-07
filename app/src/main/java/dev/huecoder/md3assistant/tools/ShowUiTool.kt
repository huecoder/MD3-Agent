package dev.huecoder.md3assistant.tools

import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolSpec
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
        description = "Показывает пользователю под твоим ответом варианты быстрого ответа (чипы) и/или кнопки. " +
            "Вызывай в самом конце, после текста ответа, когда есть очевидные следующие шаги. " +
            "Чип при нажатии отправляет свой текст от имени пользователя.",
        parameters = objectSchema(required = emptyList()) {
            putJsonObject("suggestions") {
                put("type", "array")
                put("description", "До 4 коротких вариантов ответа от лица пользователя")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("buttons") {
                put("type", "array")
                put("description", "До 4 кнопок с действиями")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        stringProperty("label", "Текст на кнопке")
                        putJsonObject("action") {
                            put("type", "string")
                            putJsonArray("enum") { ButtonAction.entries.forEach { add(JsonPrimitive(it.wireName)) } }
                            put("description", "open_url открывает ссылку, copy копирует value, send отправляет value как сообщение")
                        }
                        stringProperty("value", "URL, текст для копирования или сообщение")
                    }
                    putJsonArray("required") { listOf("label", "action", "value").forEach { add(JsonPrimitive(it)) } }
                }
            }
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val ui = ReplyUi.parse(arguments)
        return if (ui.isEmpty) ToolOutcome.error("Нечего показывать: нет ни корректных вариантов, ни кнопок") else ToolOutcome("Показано пользователю")
    }

    companion object {
        const val NAME = "show_ui"
    }
}
