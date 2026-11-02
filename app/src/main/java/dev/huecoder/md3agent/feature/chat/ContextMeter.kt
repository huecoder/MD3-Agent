package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole

data class ContextMeter(val used: Int, val limit: Int, val custom: Int? = null) {
    val fraction: Float get() = (used.toFloat() / limit).coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_LIMIT = 128_000

        fun limitFor(contextLength: Int?): Int = contextLength?.takeIf { it > 0 } ?: DEFAULT_LIMIT

        fun usedBy(messages: List<MessageEntity>): Int? = messages.asReversed().firstNotNullOfOrNull { row ->
            row.promptTokens?.takeIf { row.role == MessageRole.ASSISTANT }?.let { it + (row.completionTokens ?: 0) }
        }
    }
}
