package dev.huecoder.md3agent.llm.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object ModelInfoParser {
    private const val IMAGE = "image"
    private const val TEXT = "text"
    private const val MODALITY_ARROW = "->"
    private const val GEMINI_MODEL_PREFIX = "models/"

    fun parseList(root: JsonElement): List<ModelInfo> {
        val entries = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["data"] ?: root["models"]) as? JsonArray
            else -> null
        } ?: throw IllegalArgumentException("No model list in the response")
        return entries.mapNotNull { (it as? JsonObject)?.let(::parse) }.distinctBy { it.id }.sortedBy { it.id }
    }

    fun parse(entry: JsonObject): ModelInfo? {
        val geminiName = entry.string("name")?.takeIf { it.startsWith(GEMINI_MODEL_PREFIX) }
        val id = entry.string("id") ?: geminiName?.removePrefix(GEMINI_MODEL_PREFIX) ?: return null
        val architecture = entry.obj("architecture")
        val capabilities = entry.obj("capabilities")
        val parameters = entry.strings("supported_parameters")
        val inputs = architecture?.strings("input_modalities") ?: entry.obj("modalities")?.strings("input")
            ?: entry.strings("input_modalities") ?: architecture?.string("modality")?.modalitySide(input = true)
        val outputs = architecture?.strings("output_modalities") ?: entry.obj("modalities")?.strings("output")
            ?: entry.strings("output_modalities") ?: architecture?.string("modality")?.modalitySide(input = false)

        val effortField = parameters?.contains("reasoning_effort") == true
        val reasoningObject = parameters?.any { it == "reasoning" || it == "include_reasoning" } == true
        val reasoning = capabilities?.flag("reasoning", "thinking") ?: entry.flag("supports_reasoning", "reasoning", "thinking")
            ?: parameters?.let { effortField || reasoningObject }
        val efforts = (entry.strings("supported_reasoning_efforts") ?: entry.obj("reasoning")?.strings("efforts")
            ?: capabilities?.strings("reasoning_efforts")).orEmpty().mapNotNull(ReasoningEffort::fromWire)

        return ModelInfo(
            id = id,
            name = entry.string("displayName") ?: entry.string("name")?.takeIf { geminiName == null } ?: entry.string("display_name"),
            contextLength = entry.int("context_length") ?: entry.int("context_window") ?: entry.int("max_context_length") ?: entry.int("inputTokenLimit")
                ?: entry.obj("top_provider")?.int("context_length"),
            vision = capabilities?.flag("vision", "image_input") ?: entry.flag("supports_vision", "vision") ?: inputs?.contains(IMAGE),
            reasoning = reasoning,
            output = outputs?.let(::outputOf) ?: capabilities?.flag("image_generation")?.let { if (it) ModelOutput.TEXT_AND_IMAGE else null },
            reasoningEfforts = efforts,
            reasoningControl = when {
                reasoningObject -> ReasoningControl.REASONING_OBJECT
                effortField -> ReasoningControl.EFFORT_FIELD
                else -> null
            },
            toolUse = parameters?.contains("tools") ?: capabilities?.flag("function_calling", "tools") ?: entry.flag("supports_tools"),
        )
    }

    private fun outputOf(modalities: List<String>): ModelOutput? {
        val image = IMAGE in modalities
        val text = TEXT in modalities
        return when {
            image && text -> ModelOutput.TEXT_AND_IMAGE
            image -> ModelOutput.IMAGE
            text -> ModelOutput.TEXT
            else -> null
        }
    }

    private fun String.modalitySide(input: Boolean): List<String>? {
        if (!contains(MODALITY_ARROW)) return null
        val side = if (input) substringBefore(MODALITY_ARROW) else substringAfter(MODALITY_ARROW)
        return side.split('+').map { it.trim().lowercase() }
    }

    private fun JsonObject.obj(name: String) = this[name] as? JsonObject

    private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.strings(name: String) =
        (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }

    private fun JsonObject.flag(vararg names: String): Boolean? =
        names.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.booleanOrNull }
}
