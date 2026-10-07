package dev.huecoder.md3assistant.llm

class ToolCallAccumulator {
    private class Partial(var id: String? = null, var name: String = "", val arguments: StringBuilder = StringBuilder())

    private val partials = sortedMapOf<Int, Partial>()

    fun add(delta: CompletionEvent.ToolCallDelta) {
        val partial = partials.getOrPut(delta.index) { Partial() }
        delta.id?.takeIf { it.isNotEmpty() }?.let { partial.id = it }
        delta.name?.takeIf { it.isNotEmpty() && it != partial.name }?.let { partial.name += it }
        delta.arguments?.let { partial.arguments.append(it) }
    }

    fun calls(): List<ToolCall> = partials.mapNotNull { (index, partial) ->
        partial.name.takeIf { it.isNotEmpty() }?.let { name ->
            ToolCall(
                id = partial.id ?: "$SYNTHETIC_ID_PREFIX$index",
                name = name,
                arguments = partial.arguments.toString().ifBlank { EMPTY_ARGUMENTS },
            )
        }
    }

    private companion object {
        const val SYNTHETIC_ID_PREFIX = "call_"
        const val EMPTY_ARGUMENTS = "{}"
    }
}
