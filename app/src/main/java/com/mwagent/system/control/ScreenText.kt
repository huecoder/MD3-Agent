package com.mwagent.system.control

object ScreenText {
    fun describe(
        packageName: String?,
        screenWidth: Int,
        screenHeight: Int,
        cutouts: List<ScreenBox>,
        elements: List<ScreenElement>,
        hidden: Int,
        detailed: Boolean = false,
        find: ElementSelector? = null,
    ): String = buildString {
        append("App: ${packageName ?: "unknown"}, screen ${screenWidth}x$screenHeight")
        if (cutouts.isNotEmpty()) append(", cutouts ").append(cutouts.joinToString(" ") { "${it.left},${it.top}-${it.right},${it.bottom}" })
        appendLine()
        val shown = if (find == null || find.isBlank) elements else ElementPicker.filter(find, elements)
        if (find != null && !find.isBlank) {
            appendLine("Found ${shown.size} of ${elements.size + hidden} elements for ${find.describe()}:")
        }
        if (elements.isEmpty()) {
            append("No readable elements, the app may hide its content. Use coordinates or global actions.")
            return@buildString
        }
        shown.forEach { appendLine(line(it, detailed || find != null)) }
        if (shown.isEmpty()) appendLine("Nothing matches, read the whole screen without find or scroll to look further.")
        if (hidden > 0 && (find == null || find.isBlank)) {
            appendLine("+$hidden more elements are not shown, scroll or use find to look for something specific.")
        }
    }.trimEnd()

    fun line(element: ScreenElement, detailed: Boolean): String = buildString {
        append('[').append(element.number).append("] ").append(element.role)
        if (element.label.isNotEmpty()) append(" \"").append(element.label).append('"')
        val traits = listOfNotNull(
            "tap".takeIf { element.clickable },
            "input".takeIf { element.editable },
            element.scrollMoves.takeIf { element.scrollable }?.let { "scroll$it" } ?: "scroll".takeIf { element.scrollable },
            element.checked?.let { if (it) "checked" else "unchecked" },
            "focused".takeIf { element.focused },
            "partly visible".takeIf { element.partial },
        )
        if (traits.isNotEmpty()) append(" (").append(traits.joinToString()).append(')')
        append(" @").append(element.bounds.centerX).append(',').append(element.bounds.centerY)
        if (detailed) {
            val bounds = element.bounds
            append(" bounds=[").append(bounds.left).append(',').append(bounds.top).append("][").append(bounds.right).append(',').append(bounds.bottom).append(']')
            append(" class=").append(element.className)
            element.viewId?.let { append(" id=").append(it) }
        }
    }
}
