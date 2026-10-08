package dev.huecoder.md3assistant.tools

import dev.huecoder.md3assistant.data.attachments.Attachment
import dev.huecoder.md3assistant.data.settings.ToolPermission
import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

interface Tool {
    val spec: ToolSpec

    val endsTurn: Boolean get() = false

    val defaultPermission: ToolPermission get() = ToolPermission.AUTO

    val timeoutMs: Long get() = DEFAULT_TOOL_TIMEOUT_MS

    fun promptNote(settings: ToolSettings): String? = null

    suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome
}

const val DEFAULT_TOOL_TIMEOUT_MS = 45_000L

data class ToolOutcome(
    val content: String,
    val isError: Boolean = false,
    val isDenied: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
) {
    companion object {
        fun error(message: String) = ToolOutcome(message, isError = true)

        fun denied() = ToolOutcome("The user declined this action. Do not retry it unless they ask.", isError = true, isDenied = true)
    }
}

class ToolArgumentException(message: String) : IllegalArgumentException(message)

fun JsonObject.requireString(name: String): String =
    optionalString(name) ?: throw ToolArgumentException("A non-empty string parameter '$name' is required")

fun JsonObject.optionalString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

fun JsonObject.optionalInt(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

fun JsonObject.optionalBoolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.optionalArray(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())

fun objectSchema(required: List<String>, properties: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties", properties)
    putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
}

fun JsonObjectBuilder.stringProperty(name: String, description: String) = putJsonObject(name) {
    put("type", "string")
    put("description", description)
}

fun JsonObjectBuilder.integerProperty(name: String, description: String, minimum: Int, maximum: Int) = putJsonObject(name) {
    put("type", "integer")
    put("description", description)
    put("minimum", minimum)
    put("maximum", maximum)
}

fun JsonObjectBuilder.booleanProperty(name: String, description: String) = putJsonObject(name) {
    put("type", "boolean")
    put("description", description)
}

fun JsonObjectBuilder.stringArrayProperty(name: String, description: String) = putJsonObject(name) {
    put("type", "array")
    put("description", description)
    putJsonObject("items") { put("type", "string") }
}
