package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ApiFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ReasoningBlock(val json: JsonObject, val anchor: String? = null)

@Serializable
data class ReasoningTrace(
    val format: ApiFormat,
    val model: String,
    val prompt: String,
    val blocks: List<ReasoningBlock>,
) {
    fun matches(format: ApiFormat, model: String, prompt: String) = this.format == format && this.model == model && this.prompt == prompt

    fun leading(): List<JsonObject> = blocks.filter { it.anchor == null }.map { it.json }

    fun before(callId: String): List<JsonObject> = blocks.filter { it.anchor == callId }.map { it.json }
}

fun promptFingerprint(turns: List<ChatTurn>, tools: List<ToolSpec>): String {
    val system = turns.filter { it.role == ChatTurn.Role.SYSTEM }.joinToString(PART_SEPARATOR) { it.content }
    val specs = tools.joinToString(PART_SEPARATOR) { listOf(it.name, it.description, it.parameters).joinToString(FIELD_SEPARATOR) }
    return (system + PART_SEPARATOR + specs).hashCode().toUInt().toString(HEX)
}

fun List<ChatTurn>.withoutReasoning(): List<ChatTurn> = map { if (it.reasoning == null) it else it.copy(reasoning = null) }

fun List<ChatTurn>.carriesReasoning(): Boolean = any { it.reasoning != null }

fun ProviderException.Http.rejectsReplayedReasoning(): Boolean =
    code == BAD_REQUEST && REJECTION_MARKERS.any { message.orEmpty().contains(it, ignoreCase = true) }

private const val PART_SEPARATOR = "\u0001"
private const val FIELD_SEPARATOR = "\u0000"
private const val HEX = 16
private const val BAD_REQUEST = 400
private val REJECTION_MARKERS = listOf("thinking", "signature", "reasoning")
