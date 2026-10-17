package com.mwagent.llm.models

import kotlinx.serialization.Serializable

@Serializable
enum class ReasoningEffort(val wireName: String) {
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    ;

    companion object {
        val STANDARD = listOf(LOW, MEDIUM, HIGH)

        fun fromWire(value: String): ReasoningEffort? = entries.firstOrNull { it.wireName.equals(value.trim(), ignoreCase = true) }
    }
}

@Serializable
enum class ReasoningControl {
    EFFORT_FIELD,
    REASONING_OBJECT,
}

@Serializable
enum class ModelOutput { TEXT, IMAGE, TEXT_AND_IMAGE }

@Serializable
data class ModelInfo(
    val id: String,
    val name: String? = null,
    val contextLength: Int? = null,
    val vision: Boolean? = null,
    val reasoning: Boolean? = null,
    val output: ModelOutput? = null,
    val reasoningEfforts: List<ReasoningEffort> = emptyList(),
    val reasoningControl: ReasoningControl? = null,
    val toolUse: Boolean? = null,
) {
    val family: ModelFamily? get() = ModelFamily.detect(id)

    val displayName: String get() = name?.substringAfter(": ")?.takeIf { it.isNotBlank() } ?: id.substringAfterLast('/')

    val traits: ModelTraits get() = ModelTraits.of(this)
}

data class ModelTraits(
    val vision: Boolean,
    val reasoning: Boolean,
    val output: ModelOutput,
    val reasoningEfforts: List<ReasoningEffort>,
    val reasoningControl: ReasoningControl,
) {
    val generatesImages: Boolean get() = output != ModelOutput.TEXT

    companion object {
        fun of(info: ModelInfo): ModelTraits {
            val guess = ModelHeuristics.guess(info.id)
            val reasoning = info.reasoning ?: guess.reasoning
            return ModelTraits(
                vision = info.vision ?: guess.vision,
                reasoning = reasoning,
                output = info.output ?: guess.output,
                reasoningEfforts = info.reasoningEfforts.ifEmpty { if (reasoning) ReasoningEffort.STANDARD else emptyList() },
                reasoningControl = info.reasoningControl ?: ReasoningControl.EFFORT_FIELD,
            )
        }
    }
}
