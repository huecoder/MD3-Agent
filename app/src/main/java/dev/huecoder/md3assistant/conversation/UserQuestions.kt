package dev.huecoder.md3assistant.conversation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class QuestionOption(val label: String, val description: String?)

data class UserQuestion(
    val question: String,
    val header: String?,
    val options: List<QuestionOption>,
    val multiSelect: Boolean,
    val allowCustom: Boolean,
)

sealed interface QuestionAnswer {
    data class Answered(val chosen: List<String>, val custom: String?) : QuestionAnswer
    data object Skipped : QuestionAnswer
}

data class PendingQuestion(val id: String, val chatId: String, val question: UserQuestion)

class UserQuestions {
    private val waiting = MutableStateFlow<Map<String, Waiting>>(emptyMap())
    private val _pending = MutableStateFlow<Map<String, PendingQuestion>>(emptyMap())

    val pending: StateFlow<Map<String, PendingQuestion>> = _pending.asStateFlow()

    suspend fun ask(chatId: String, question: UserQuestion): QuestionAnswer {
        val entry = Waiting(PendingQuestion(UUID.randomUUID().toString(), chatId, question), CompletableDeferred())
        waiting.update { it + (chatId to entry) }
        _pending.update { it + (chatId to entry.question) }
        try {
            return entry.answer.await()
        } finally {
            waiting.update { current -> if (current[chatId] === entry) current - chatId else current }
            _pending.update { current -> if (current[chatId] === entry.question) current - chatId else current }
        }
    }

    fun answer(chatId: String, questionId: String, answer: QuestionAnswer) {
        waiting.value[chatId]?.takeIf { it.question.id == questionId }?.answer?.complete(answer)
    }

    private class Waiting(val question: PendingQuestion, val answer: CompletableDeferred<QuestionAnswer>)
}
