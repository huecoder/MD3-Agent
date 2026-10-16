package dev.huecoder.md3agent.feature.files

import kotlin.math.max
import kotlin.math.min

internal class RangeSelect {
    private var base: Set<String>? = null

    fun extend(selection: Set<String>, ordered: List<String>, anchor: String, current: String): Set<String> {
        val from = ordered.indexOf(anchor)
        val to = ordered.indexOf(current)
        if (from < 0 || to < 0) return selection
        val untouched = base ?: selection.also { base = it }
        return untouched + ordered.subList(min(from, to), max(from, to) + 1)
    }

    fun end() {
        base = null
    }
}
