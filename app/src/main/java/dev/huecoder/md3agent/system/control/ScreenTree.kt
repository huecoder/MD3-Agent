package dev.huecoder.md3agent.system.control

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenElement(
    val number: Int,
    val role: String,
    val label: String,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checked: Boolean?,
    val focused: Boolean,
)

class ScreenSnapshot(
    val packageName: String?,
    val elements: List<ScreenElement>,
    val screenWidth: Int,
    val screenHeight: Int,
    internal val nodes: Map<Int, AccessibilityNodeInfo>,
) {
    fun describe(): String = buildString {
        appendLine("App: ${packageName ?: "unknown"}, screen ${screenWidth}x$screenHeight")
        if (elements.isEmpty()) {
            append("No readable elements, the app may hide its content. Use coordinates or global actions.")
            return@buildString
        }
        elements.forEach { element ->
            append('[').append(element.number).append("] ").append(element.role)
            if (element.label.isNotEmpty()) append(" \"").append(element.label).append('"')
            val traits = listOfNotNull(
                "tap".takeIf { element.clickable },
                "input".takeIf { element.editable },
                "scroll".takeIf { element.scrollable },
                element.checked?.let { if (it) "checked" else "unchecked" },
                "focused".takeIf { element.focused },
            )
            if (traits.isNotEmpty()) append(" (").append(traits.joinToString()).append(')')
            append(" @").append(element.bounds.centerX()).append(',').append(element.bounds.centerY())
            appendLine()
        }
    }.trimEnd()
}

object ScreenTree {
    private const val MAX_ELEMENTS = 160
    private const val MAX_LABEL_CHARS = 120
    private val WHITESPACE = Regex("""\s+""")

    fun capture(root: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): ScreenSnapshot {
        val elements = mutableListOf<ScreenElement>()
        val nodes = mutableMapOf<Int, AccessibilityNodeInfo>()
        val screen = Rect(0, 0, screenWidth, screenHeight)
        fun visit(node: AccessibilityNodeInfo) {
            if (elements.size >= MAX_ELEMENTS || !node.isVisibleToUser) return
            val bounds = Rect().also(node::getBoundsInScreen)
            if (!bounds.intersect(screen) || bounds.isEmpty) return
            val label = labelOf(node)
            val interactive = node.isClickable || node.isLongClickable || node.isEditable || node.isScrollable || node.isCheckable
            if (label.isNotEmpty() || interactive) {
                val number = elements.size + 1
                elements += ScreenElement(
                    number = number,
                    role = roleOf(node),
                    label = label,
                    bounds = bounds,
                    clickable = node.isClickable || node.isLongClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    checked = if (node.isCheckable) node.isChecked else null,
                    focused = node.isFocused,
                )
                nodes[number] = node
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        root?.let(::visit)
        return ScreenSnapshot(root?.packageName?.toString(), elements, screenWidth, screenHeight, nodes)
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        val parts = listOfNotNull(node.text, node.contentDescription, node.hintText.takeIf { node.text.isNullOrEmpty() })
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return parts.joinToString(" · ").replace(WHITESPACE, " ").take(MAX_LABEL_CHARS)
    }

    private fun roleOf(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString()?.substringAfterLast('.').orEmpty()
        return when {
            node.isEditable -> "Input"
            node.isCheckable && className.contains("Switch", ignoreCase = true) -> "Switch"
            node.isCheckable -> "Checkbox"
            className.contains("Button", ignoreCase = true) -> "Button"
            className.contains("Image", ignoreCase = true) -> "Image"
            node.isScrollable -> "List"
            className.isEmpty() || className == "View" || className == "ViewGroup" -> if (node.isClickable) "Item" else "Text"
            className == "TextView" -> if (node.isClickable) "Item" else "Text"
            else -> className
        }
    }
}
