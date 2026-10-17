package com.mwagent.llm

import com.mwagent.llm.models.ModelOutput
import com.mwagent.llm.models.ReasoningControl
import com.mwagent.llm.models.ReasoningEffort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

data class ChatTurn(
    val role: Role,
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val cacheBreakpoint: Boolean = false,
    val reasoning: ReasoningTrace? = null,
) {
    enum class Role(val wireName: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant"), TOOL("tool") }
}

@Serializable
data class ToolCall(val id: String, val name: String, val arguments: String, val signature: String? = null)

data class CompletionOptions(
    val reasoningEffort: ReasoningEffort? = null,
    val reasoningControl: ReasoningControl = ReasoningControl.EFFORT_FIELD,
    val output: ModelOutput = ModelOutput.TEXT,
    val explicitCaching: Boolean = false,
)

data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)

sealed interface CompletionEvent {
    data class ContentDelta(val text: String) : CompletionEvent
    data class ReasoningDelta(val text: String) : CompletionEvent
    data class ImageOutput(val url: String) : CompletionEvent
    data class ReasoningBlockDone(val block: ReasoningBlock) : CompletionEvent
    data class ToolCallDelta(val index: Int, val id: String?, val name: String?, val arguments: String?, val signature: String? = null) : CompletionEvent
    data class Finished(val reason: String?) : CompletionEvent
    data class Usage(val usage: TokenUsage) : CompletionEvent
}

data class TokenUsage(val promptTokens: Int, val completionTokens: Int, val cachedTokens: Int = 0) {
    operator fun plus(other: TokenUsage) =
        TokenUsage(promptTokens + other.promptTokens, completionTokens + other.completionTokens, cachedTokens + other.cachedTokens)
}
