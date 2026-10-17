package com.mwagent.system.control

data class ElementSelector(
    val text: String? = null,
    val id: String? = null,
    val role: String? = null,
    val exact: Boolean = false,
    val index: Int? = null,
) {
    val isBlank: Boolean get() = text == null && id == null && role == null

    fun describe(): String = listOfNotNull(
        text?.let { "text \"$it\"" },
        id?.let { "id $it" },
        role?.let { "role $it" },
        index?.let { "#$it" },
    ).joinToString(" ")
}

sealed interface Pick {
    data class Found(val element: ScreenElement) : Pick
    data class Ambiguous(val candidates: List<ScreenElement>) : Pick
    data class Missing(val matches: Int) : Pick
}

object ElementPicker {
    const val MAX_CANDIDATES = 8

    fun matches(selector: ElementSelector, element: ScreenElement): Boolean {
        selector.text?.let { wanted ->
            val matched = if (selector.exact) element.labelParts.any { it.equals(wanted, ignoreCase = true) } else element.label.contains(wanted, ignoreCase = true)
            if (!matched) return false
        }
        selector.id?.let { wanted ->
            val shortId = wanted.substringAfter(":id/")
            if (element.viewId?.equals(shortId, ignoreCase = true) != true) return false
        }
        selector.role?.let { wanted -> if (!element.role.equals(wanted, ignoreCase = true)) return false }
        return true
    }

    fun filter(selector: ElementSelector, elements: List<ScreenElement>): List<ScreenElement> =
        if (selector.isBlank) elements else elements.filter { matches(selector, it) }

    fun pick(selector: ElementSelector, elements: List<ScreenElement>): Pick {
        val matching = filter(selector, elements)
        selector.index?.let { index -> return matching.getOrNull(index - 1)?.let(Pick::Found) ?: Pick.Missing(matching.size) }
        return when {
            matching.isEmpty() -> Pick.Missing(0)
            matching.size == 1 -> Pick.Found(matching.single())
            else -> exactlyOne(selector, matching)?.let(Pick::Found) ?: Pick.Ambiguous(matching.take(MAX_CANDIDATES))
        }
    }

    private fun exactlyOne(selector: ElementSelector, matching: List<ScreenElement>): ScreenElement? {
        val wanted = selector.text ?: return null
        return matching.filter { element -> element.labelParts.any { it.equals(wanted, ignoreCase = true) } }.singleOrNull()
    }
}
