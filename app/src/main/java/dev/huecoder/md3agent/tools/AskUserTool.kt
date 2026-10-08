package dev.huecoder.md3agent.tools

import dev.huecoder.md3agent.conversation.PromptAnswer
import dev.huecoder.md3agent.conversation.QuestionOption
import dev.huecoder.md3agent.conversation.UserQuestion
import dev.huecoder.md3agent.conversation.UserPrompts
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AskUserTool(private val prompts: UserPrompts) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Asks the user a multiple choice question and waits for the answer. Use it when you need a decision or a " +
            "preference before going on and the choices are clear. The user can also type their own answer or skip.",
        parameters = objectSchema(required = listOf("question", "options")) {
            stringProperty("question", "The question, ending with a question mark")
            stringProperty("header", "Very short topic label, up to 3 words")
            putJsonObject("options") {
                put("type", "array")
                put("description", "2 to $MAX_OPTIONS distinct choices")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        stringProperty("label", "Short choice text")
                        stringProperty("description", "Optional one line explanation")
                    }
                    putJsonArray("required") { add(JsonPrimitive("label")) }
                }
            }
            booleanProperty("multi_select", "Let the user pick several options")
            booleanProperty("allow_custom", "Let the user type their own answer, true by default")
        },
    )

    override val timeoutMs: Long = WAIT_FOR_ANSWER_MS

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val question = parse(arguments)
        return when (val answer = prompts.ask(context.chatId, question)) {
            is PromptAnswer.Chosen -> ToolOutcome(describe(answer))
            PromptAnswer.Skipped, is PromptAnswer.Submitted -> ToolOutcome(SKIPPED)
        }
    }

    private fun parse(arguments: JsonObject): UserQuestion {
        val options = arguments.optionalArray("options").mapNotNull { element ->
            val option = element as? JsonObject
            val label = option?.optionalString("label")?.take(MAX_LABEL_CHARS) ?: (element as? JsonPrimitive)?.content?.trim()
            label?.takeIf { it.isNotEmpty() }?.let { QuestionOption(it, option?.optionalString("description")?.take(MAX_DESCRIPTION_CHARS)) }
        }.distinctBy { it.label }.take(MAX_OPTIONS)
        if (options.size < MIN_OPTIONS) throw ToolArgumentException("Give at least $MIN_OPTIONS options")
        return UserQuestion(
            question = arguments.requireString("question").take(MAX_QUESTION_CHARS),
            header = arguments.optionalString("header")?.take(MAX_HEADER_CHARS),
            options = options,
            multiSelect = arguments.optionalBoolean("multi_select") ?: false,
            allowCustom = arguments.optionalBoolean("allow_custom") ?: true,
        )
    }

    private fun describe(answer: PromptAnswer.Chosen): String = buildString {
        if (answer.chosen.isNotEmpty()) append("The user chose: ").append(answer.chosen.joinToString("; "))
        answer.custom?.let {
            if (isNotEmpty()) append(". ")
            append("The user wrote: ").append(it)
        }
    }

    companion object {
        const val NAME = "ask_user"
        private const val SKIPPED = "The user skipped the question. Continue with your best judgement or ask in plain text."
        private const val MIN_OPTIONS = 2
        private const val MAX_OPTIONS = 6
        private const val MAX_LABEL_CHARS = 80
        private const val MAX_DESCRIPTION_CHARS = 200
        private const val MAX_QUESTION_CHARS = 500
        private const val MAX_HEADER_CHARS = 24
        private const val WAIT_FOR_ANSWER_MS = 24 * 60 * 60 * 1000L
    }
}
