package dev.huecoder.md3assistant.tools

import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

interface Tool {
    val spec: ToolSpec

    val endsTurn: Boolean get() = false

    suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome
}

data class ToolOutcome(val content: String, val isError: Boolean = false) {
    companion object {
        fun error(message: String) = ToolOutcome(message, isError = true)
    }
}

class ToolArgumentException(message: String) : IllegalArgumentException(message)

fun JsonObject.requireString(name: String): String =
    optionalString(name) ?: throw ToolArgumentException("Нужен непустой строковый параметр '$name'")

fun JsonObject.optionalString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

fun JsonObject.optionalInt(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

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
