package dev.huecoder.md3agent.mcp

import dev.huecoder.md3agent.conversation.FormField
import dev.huecoder.md3agent.conversation.FormFieldKind
import dev.huecoder.md3agent.conversation.FormRequest
import dev.huecoder.md3agent.conversation.PromptAnswer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object McpElicitation {
    const val METHOD = "elicitation/create"
    private const val ACCEPT = "accept"
    private const val DECLINE = "decline"

    fun form(serverName: String, params: JsonObject?): FormRequest {
        val schema = params?.get("requestedSchema") as? JsonObject
        val required = (schema?.get("required") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        val properties = schema?.get("properties") as? JsonObject ?: JsonObject(emptyMap())
        return FormRequest(
            source = serverName,
            message = params?.string("message").orEmpty(),
            fields = properties.mapNotNull { (key, raw) -> (raw as? JsonObject)?.let { field(key, it, key in required) } },
        )
    }

    fun result(form: FormRequest, answer: PromptAnswer): JsonObject = buildJsonObject {
        val values = (answer as? PromptAnswer.Submitted)?.values
        if (values == null) {
            put("action", DECLINE)
            return@buildJsonObject
        }
        put("action", ACCEPT)
        putJsonObject("content") {
            form.fields.forEach { field ->
                val value = values[field.key] ?: return@forEach
                when (val kind = field.kind) {
                    FormFieldKind.Toggle -> put(field.key, value.toBoolean())
                    is FormFieldKind.Number -> if (kind.integer) value.toLongOrNull()?.let { put(field.key, it) } else value.toDoubleOrNull()?.let { put(field.key, it) }
                    else -> put(field.key, value)
                }
            }
        }
    }

    val declined: JsonObject = buildJsonObject { put("action", DECLINE) }

    private fun field(key: String, property: JsonObject, required: Boolean): FormField {
        val type = property.string("type")
        val options = (property["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val kind = when {
            options != null -> FormFieldKind.Choice(options, (property["enumNames"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.size == options.size } ?: options)
            type == "boolean" -> FormFieldKind.Toggle
            type == "number" || type == "integer" -> FormFieldKind.Number(type == "integer", property.number("minimum"), property.number("maximum"))
            else -> FormFieldKind.Text
        }
        return FormField(
            key = key,
            title = property.string("title") ?: key,
            description = property.string("description"),
            kind = kind,
            required = required,
            default = (property["default"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(name: String) = (this[name] as? JsonPrimitive)?.doubleOrNull
}
