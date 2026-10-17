package com.mwagent.system.control

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction

class ScreenSnapshot(
    val packageName: String?,
    val elements: List<ScreenElement>,
    val hidden: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val cutouts: List<ScreenBox>,
    val probe: ScreenProbe,
    internal val nodes: Map<Int, AccessibilityNodeInfo>,
) {
    fun describe(detailed: Boolean = false, find: ElementSelector? = null): String =
        ScreenText.describe(packageName, screenWidth, screenHeight, cutouts, elements, hidden, detailed, find)
}

object ScreenTree {
    private const val MAX_ELEMENTS = 160
    private const val MAX_LABEL_CHARS = 120
    private const val FINGERPRINT_SEED = 1125899906842597L
    private const val FINGERPRINT_PRIME = 31L
    private val WHITESPACE = Regex("""\s+""")

    fun capture(root: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int, cutouts: List<ScreenBox> = emptyList()): ScreenSnapshot {
        val elements = mutableListOf<ScreenElement>()
        val nodes = mutableMapOf<Int, AccessibilityNodeInfo>()
        val labels = mutableListOf<String>()
        var hidden = 0
        var fingerprint = FINGERPRINT_SEED
        walk(root, Rect(0, 0, screenWidth, screenHeight)) { node, full, clipped ->
            val label = labelOf(node)
            if (label.isNotEmpty()) labels += label
            fingerprint = mix(fingerprint, label.hashCode(), clipped.left, clipped.top, clipped.right, clipped.bottom, flagsOf(node))
            val interactive = node.isClickable || node.isLongClickable || node.isEditable || node.isScrollable || node.isCheckable
            if (label.isEmpty() && !interactive) return@walk
            if (elements.size >= MAX_ELEMENTS) {
                hidden++
                return@walk
            }
            val number = elements.size + 1
            elements += ScreenElement(
                number = number,
                role = roleOf(node),
                label = label,
                bounds = ScreenBox.of(clipped),
                clickable = node.isClickable || node.isLongClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                checked = if (node.isCheckable) node.isChecked else null,
                focused = node.isFocused,
                viewId = node.viewIdResourceName?.substringAfter(":id/"),
                className = node.className?.toString().orEmpty(),
                partial = clipped != full,
                scrollMoves = if (node.isScrollable) scrollMovesOf(node) else "",
            )
            nodes[number] = node
        }
        val packageName = root?.packageName?.toString()
        return ScreenSnapshot(packageName, elements, hidden, screenWidth, screenHeight, cutouts, ScreenProbe(packageName, fingerprint, labels), nodes)
    }

    fun probe(root: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): ScreenProbe {
        val labels = mutableListOf<String>()
        var fingerprint = FINGERPRINT_SEED
        walk(root, Rect(0, 0, screenWidth, screenHeight)) { node, _, clipped ->
            val label = labelOf(node)
            if (label.isNotEmpty()) labels += label
            fingerprint = mix(fingerprint, label.hashCode(), clipped.left, clipped.top, clipped.right, clipped.bottom, flagsOf(node))
        }
        return ScreenProbe(root?.packageName?.toString(), fingerprint, labels)
    }

    fun findAnywhere(root: AccessibilityNodeInfo?, text: String): List<AccessibilityNodeInfo> =
        root?.findAccessibilityNodeInfosByText(text).orEmpty()

    private fun walk(root: AccessibilityNodeInfo?, screen: Rect, visit: (AccessibilityNodeInfo, Rect, Rect) -> Unit) {
        fun go(node: AccessibilityNodeInfo) {
            if (!node.isVisibleToUser) return
            val full = Rect().also(node::getBoundsInScreen)
            val clipped = Rect(full)
            if (!clipped.intersect(screen) || clipped.isEmpty) return
            visit(node, full, clipped)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::go)
        }
        root?.let(::go)
    }

    private fun mix(seed: Long, vararg parts: Int): Long = parts.fold(seed) { acc, part -> acc * FINGERPRINT_PRIME + part }

    private fun flagsOf(node: AccessibilityNodeInfo): Int =
        (if (node.isChecked) 1 else 0) or (if (node.isFocused) 2 else 0) or (if (node.isSelected) 4 else 0) or (if (node.isEnabled) 8 else 0)

    private fun scrollMovesOf(node: AccessibilityNodeInfo): String {
        val ids = node.actionList.map { it.id }.toSet()
        val moves = buildString {
            if (AccessibilityAction.ACTION_SCROLL_DOWN.id in ids) append('↓')
            if (AccessibilityAction.ACTION_SCROLL_UP.id in ids) append('↑')
            if (AccessibilityAction.ACTION_SCROLL_RIGHT.id in ids) append('→')
            if (AccessibilityAction.ACTION_SCROLL_LEFT.id in ids) append('←')
        }
        if (moves.isNotEmpty()) return moves
        return buildString {
            if (AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in ids) append('↓')
            if (AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in ids) append('↑')
        }
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        val parts = listOfNotNull(node.text, node.contentDescription, node.hintText.takeIf { node.text.isNullOrEmpty() })
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return parts.joinToString(ScreenElement.LABEL_SEPARATOR).replace(WHITESPACE, " ").take(MAX_LABEL_CHARS)
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
