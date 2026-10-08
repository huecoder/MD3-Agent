package dev.huecoder.md3assistant.conversation

import dev.huecoder.md3assistant.llm.ToolCall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class ApprovalDecision { ALLOW, DENY, ALLOW_FOR_SESSION }

class ToolApprovals {
    private val waiting = MutableStateFlow<Map<String, Waiting>>(emptyMap())
    private val sessionAllowed = ConcurrentHashMap<String, Set<String>>()

    fun isAllowedForSession(chatId: String, toolName: String): Boolean = toolName in sessionAllowed[chatId].orEmpty()

    suspend fun request(chatId: String, call: ToolCall): Boolean {
        val entry = Waiting(call, CompletableDeferred())
        waiting.update { it + (chatId to entry) }
        try {
            val decision = entry.decision.await()
            if (decision == ApprovalDecision.ALLOW_FOR_SESSION) sessionAllowed.merge(chatId, setOf(call.name), Set<String>::plus)
            return decision != ApprovalDecision.DENY
        } finally {
            waiting.update { current -> if (current[chatId] === entry) current - chatId else current }
        }
    }

    fun respond(chatId: String, callId: String, decision: ApprovalDecision) {
        waiting.value[chatId]?.takeIf { it.call.id == callId }?.decision?.complete(decision)
    }

    private class Waiting(val call: ToolCall, val decision: CompletableDeferred<ApprovalDecision>)
}
