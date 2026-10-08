package dev.huecoder.md3agent.conversation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class QuestionOption(val label: String, val description: String?)

sealed interface UserPrompt

data class UserQuestion(
    val question: String,
    val header: String?,
    val options: List<QuestionOption>,
    val multiSelect: Boolean,
    val allowCustom: Boolean,
) : UserPrompt

data class FormRequest(val source: String, val message: String, val fields: List<FormField>) : UserPrompt

data class FormField(
    val key: String,
    val title: String,
    val description: String?,
    val kind: FormFieldKind,
    val required: Boolean,
    val default: String?,
)

sealed interface FormFieldKind {
    data object Text : FormFieldKind
    data class Number(val integer: Boolean, val minimum: Double?, val maximum: Double?) : FormFieldKind
    data object Toggle : FormFieldKind
    data class Choice(val values: List<String>, val labels: List<String>) : FormFieldKind
}

sealed interface PromptAnswer {
    data class Chosen(val chosen: List<String>, val custom: String?) : PromptAnswer
    data class Submitted(val values: Map<String, String>) : PromptAnswer
    data object Skipped : PromptAnswer
}

data class PendingPrompt(val id: String, val chatId: String, val prompt: UserPrompt)

class UserPrompts {
    private val answers = ConcurrentHashMap<String, CompletableDeferred<PromptAnswer>>()
    private val _pending = MutableStateFlow<Map<String, PendingPrompt>>(emptyMap())

    val pending: StateFlow<Map<String, PendingPrompt>> = _pending.asStateFlow()

    suspend fun ask(chatId: String, prompt: UserPrompt): PromptAnswer {
        val entry = PendingPrompt(UUID.randomUUID().toString(), chatId, prompt)
        val answer = CompletableDeferred<PromptAnswer>()
        answers[entry.id] = answer
        _pending.update { it + (chatId to entry) }
        try {
            return answer.await()
        } finally {
            answers.remove(entry.id)
            _pending.update { current -> if (current[chatId] === entry) current - chatId else current }
        }
    }

    fun answer(promptId: String, answer: PromptAnswer) {
        answers[promptId]?.complete(answer)
    }
}
