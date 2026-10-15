package dev.huecoder.md3agent.system.control

data class ScreenChanges(val added: List<String>, val removed: List<String>) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()

    fun describe(limit: Int = MAX_LINES): String = buildString {
        added.take(limit).forEach { appendLine("+ $it") }
        if (added.size > limit) appendLine("+ …and ${added.size - limit} more new elements")
        removed.take(limit).forEach { appendLine("- $it") }
        if (removed.size > limit) appendLine("- …and ${removed.size - limit} more gone elements")
    }.trimEnd()

    companion object {
        const val MAX_LINES = 25

        fun between(before: List<ScreenElement>, after: List<ScreenElement>): ScreenChanges {
            val old = before.map(::key).toMutableList()
            val added = mutableListOf<String>()
            after.map(::key).forEach { entry -> if (!old.remove(entry)) added += entry }
            return ScreenChanges(added, old)
        }

        private fun key(element: ScreenElement): String = if (element.label.isEmpty()) element.role else "${element.role} \"${element.label}\""
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
