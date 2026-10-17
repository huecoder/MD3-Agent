package dev.huecoder.md3agent.system.notifications

import dev.huecoder.md3agent.conversation.StreamingDraft
import dev.huecoder.md3agent.llm.ToolCall

data class ProgressView(
    val elapsedMs: Long,
    val tokens: Int,
    val earlierCalls: List<ToolCall>,
    val currentCall: ToolCall?,
    val isWriting: Boolean,
    val thought: String?,
)

class ChatProgress(private val startedAt: Long) {
    private var earlierRoundsTokens = 0
    private var roundId: String? = null
    private var roundTokens = 0
    private var roundCalls = emptyList<ToolCall>()
    private val finishedCalls = ArrayDeque<ToolCall>()
    private var draft: StreamingDraft? = null

    fun observe(next: StreamingDraft) {
        if (roundId != null && roundId != next.messageId) closeRound()
        roundId = next.messageId
        roundTokens = next.usage?.completionTokens ?: estimatedTokens(next)
        roundCalls = next.toolCalls
        draft = next
    }

    fun view(now: Long): ProgressView {
        val current = draft
        val currentCall = current?.let { round ->
            round.toolCalls.firstOrNull { it.id == round.awaitingCallId }
                ?: round.toolCalls.firstOrNull { it.id in round.runningCallIds }
                ?: round.toolCalls.lastOrNull()
        }
        val sameRoundBefore = currentCall?.let { call -> roundCalls.takeWhile { it.id != call.id } }.orEmpty()
        val isWriting = current != null && current.content.isNotEmpty() && currentCall == null
        return ProgressView(
            elapsedMs = now - startedAt,
            tokens = earlierRoundsTokens + roundTokens,
            earlierCalls = (finishedCalls + sameRoundBefore).takeLast(MAX_STEPS),
            currentCall = currentCall,
            isWriting = isWriting,
            thought = current?.takeIf { currentCall == null && it.content.isEmpty() }?.reasoning?.tail(),
        )
    }

    private fun closeRound() {
        earlierRoundsTokens += roundTokens
        roundCalls.forEach { finishedCalls.addLast(it) }
        while (finishedCalls.size > MAX_STEPS) finishedCalls.removeFirst()
    }

    private fun estimatedTokens(draft: StreamingDraft): Int {
        val characters = draft.content.length + draft.reasoning.length + draft.toolCalls.sumOf { it.arguments.length }
        return characters / CHARACTERS_PER_TOKEN
    }

    private fun String.tail(): String? =
        replace(WHITESPACE, " ").trim().takeIf { it.isNotEmpty() }?.let { if (it.length > THOUGHT_CHARS) "…" + it.takeLast(THOUGHT_CHARS).trimStart() else it }

    private companion object {
        const val MAX_STEPS = 4
        const val CHARACTERS_PER_TOKEN = 3
        const val THOUGHT_CHARS = 120
        val WHITESPACE = Regex("""\s+""")
    }
}

class WorkProgress(private val now: () -> Long) {
    private val chats = LinkedHashMap<String, ChatProgress>()

    fun finished(): Set<String> = chats.keys.toSet()

    fun clear() = chats.clear()

    fun sync(drafts: Map<String, StreamingDraft>, active: Set<String>): Map<String, ProgressView> {
        chats.keys.retainAll(active)
        active.forEach { chatId ->
            val progress = chats.getOrPut(chatId) { ChatProgress(now()) }
            drafts[chatId]?.let(progress::observe)
        }
        val moment = now()
        return chats.mapValues { (_, progress) -> progress.view(moment) }
    }
}
