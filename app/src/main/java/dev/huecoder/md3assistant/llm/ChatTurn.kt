package dev.huecoder.md3assistant.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

data class ChatTurn(
    val role: Role,
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
) {
    enum class Role(val wireName: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant"), TOOL("tool") }
}

@Serializable
data class ToolCall(val id: String, val name: String, val arguments: String)

data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)

sealed interface CompletionEvent {
    data class ContentDelta(val text: String) : CompletionEvent
    data class ReasoningDelta(val text: String) : CompletionEvent
    data class ToolCallDelta(val index: Int, val id: String?, val name: String?, val arguments: String?) : CompletionEvent
    data class Finished(val reason: String?) : CompletionEvent
    data class Usage(val usage: TokenUsage) : CompletionEvent
}

data class TokenUsage(val promptTokens: Int, val completionTokens: Int) {
    operator fun plus(other: TokenUsage) = TokenUsage(promptTokens + other.promptTokens, completionTokens + other.completionTokens)
}
