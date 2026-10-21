package dev.huecoder.md3agent.feature.chat

import androidx.annotation.StringRes
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.ToolSpec
import java.util.EnumMap
import kotlin.math.roundToInt

enum class ContextPart(@StringRes val label: Int) {
    INSTRUCTIONS(R.string.chat_context_instructions),
    TOOL_DEFINITIONS(R.string.chat_context_tool_definitions),
    YOUR_MESSAGES(R.string.chat_context_your_messages),
    REPLIES(R.string.chat_context_replies),
    TOOL_CALLS(R.string.chat_context_tool_calls),
    IMAGES(R.string.chat_context_images),
}

data class ContextSlice(val part: ContextPart, val tokens: Int)

data class ContextBreakdown(val slices: List<ContextSlice>) {
    val tokens: Int get() = slices.sumOf { it.tokens }

    companion object {
        private const val ASCII_LIMIT = 128
        private const val ASCII_CHAR_TOKENS = 0.25
        private const val WIDE_CHAR_TOKENS = 0.5
        private const val IMAGE_TOKENS = 1_000.0

        fun of(turns: List<ChatTurn>, tools: List<ToolSpec>, usedTokens: Int): ContextBreakdown {
            val weights = EnumMap<ContextPart, Double>(ContextPart::class.java)
            fun add(part: ContextPart, weight: Double) {
                weights.merge(part, weight, Double::plus)
            }
            for (turn in turns) {
                val textPart = when (turn.role) {
                    ChatTurn.Role.SYSTEM -> ContextPart.INSTRUCTIONS
                    ChatTurn.Role.USER -> ContextPart.YOUR_MESSAGES
                    ChatTurn.Role.ASSISTANT -> ContextPart.REPLIES
                    ChatTurn.Role.TOOL -> ContextPart.TOOL_CALLS
                }
                add(textPart, weigh(turn.content))
                turn.toolCalls.forEach { add(ContextPart.TOOL_CALLS, weigh(it.name) + weigh(it.arguments)) }
                turn.reasoning?.blocks?.forEach { add(ContextPart.REPLIES, weigh(it.json.toString())) }
                add(ContextPart.IMAGES, turn.imageUrls.size * IMAGE_TOKENS)
            }
            tools.forEach { add(ContextPart.TOOL_DEFINITIONS, weigh(it.name) + weigh(it.description) + weigh(it.parameters.toString())) }
            val total = weights.values.sum()
            if (total <= 0) return ContextBreakdown(emptyList())
            val slices = weights.entries
                .filter { it.value > 0 }
                .map { ContextSlice(it.key, (usedTokens * it.value / total).roundToInt()) }
                .sortedByDescending { it.tokens }
            return ContextBreakdown(slices)
        }

        private fun weigh(text: String): Double = text.sumOf { if (it.code < ASCII_LIMIT) ASCII_CHAR_TOKENS else WIDE_CHAR_TOKENS }
    }
}
