package dev.huecoder.md3agent.system.control

data class ScreenChanges(
    val added: List<ScreenElement>,
    val changed: List<ScreenElement>,
    val removed: List<ScreenElement>,
    val renumbered: Boolean = false,
) {
    val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()

    fun replacesScreen(beforeCount: Int, afterCount: Int): Boolean {
        val larger = maxOf(beforeCount, afterCount)
        return larger >= NAVIGATION_MIN_ELEMENTS && (added.size + removed.size) >= larger * NAVIGATION_SHARE
    }

    fun describe(limit: Int = MAX_LINES): String {
        val lines = prioritized()
        val shown = lines.take(limit)
        return buildString {
            shown.forEach { appendLine(it) }
            if (lines.size > shown.size) appendLine("+${lines.size - shown.size} changed")
            if (renumbered) appendLine(RENUMBERED_NOTE)
        }.trimEnd()
    }

    private fun prioritized(): List<String> {
        val (actionable, plain) = added.partition { it.isActionable }
        return actionable.map { "+ ${ScreenText.line(it, detailed = false)}" } +
            changed.map { "~ ${ScreenText.line(it, detailed = false)}" } +
            plain.map { "+ ${ScreenText.line(it, detailed = false)}" } +
            removed.map { "- ${key(it)}" }
    }

    companion object {
        const val MAX_LINES = 20
        private const val NAVIGATION_MIN_ELEMENTS = 8
        private const val NAVIGATION_SHARE = 0.6f
        const val RENUMBERED_NOTE = "Other element numbers moved: address them by target text or read the screen."

        fun between(before: List<ScreenElement>, after: List<ScreenElement>): ScreenChanges {
            val unmatched = before.groupByTo(mutableMapOf(), ::key).mapValues { (_, same) -> ArrayDeque(same) }
            val added = mutableListOf<ScreenElement>()
            val changed = mutableListOf<ScreenElement>()
            var renumbered = false
            after.forEach { element ->
                val earlier = unmatched[key(element)]?.removeFirstOrNull()
                when {
                    earlier == null -> added += element
                    state(earlier) != state(element) -> changed += element
                }
                if (earlier != null && earlier.number != element.number) renumbered = true
            }
            val removed = unmatched.values.flatten().sortedBy { it.number }
            return ScreenChanges(added, changed, removed, renumbered && !(added.isEmpty() && changed.isEmpty() && removed.isEmpty()))
        }

        private fun key(element: ScreenElement): String = if (element.label.isEmpty()) element.role else "${element.role} \"${element.label}\""

        private fun state(element: ScreenElement) = Triple(element.checked, element.focused, element.scrollMoves)

        private val ScreenElement.isActionable: Boolean get() = clickable || editable || scrollable || checked != null
    }
}

class RepeatGuard(private val limit: Int = DEFAULT_LIMIT) {
    private var lastKey: String? = null
    private var streak = 0

    fun record(actionKey: String, changedScreen: Boolean): Boolean {
        if (changedScreen) {
            lastKey = null
            streak = 0
            return false
        }
        if (actionKey == lastKey) streak++ else {
            lastKey = actionKey
            streak = 1
        }
        return streak >= limit
    }

    companion object {
        const val DEFAULT_LIMIT = 3
    }
}
