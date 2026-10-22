package dev.huecoder.md3agent.data.changes

data class ChangeSource(val chatId: String, val toolCallId: String)

data class FileChange(
    val path: String,
    val created: Boolean,
    val counts: DiffCounts,
    val revisionIds: List<String>,
    val diffable: Boolean,
    val rolledBack: Boolean,
) {
    val firstId: String get() = revisionIds.first()
    val lastId: String get() = revisionIds.last()
}

data class ReplyChanges(val files: List<FileChange>) {
    val added: Int get() = files.sumOf { it.counts.added }
    val removed: Int get() = files.sumOf { it.counts.removed }
    val rolledBack: Boolean get() = files.isNotEmpty() && files.all { it.rolledBack }
}

enum class ChangeDirection { UNDO, REDO }

enum class ChangeVerdict { READY, CHANGED_SINCE, UNAVAILABLE }

data class ChangePlan(val change: FileChange, val verdict: ChangeVerdict)

sealed interface ChangeOutcome {
    val path: String

    data class Done(override val path: String) : ChangeOutcome

    data class Failed(override val path: String, val detail: String?) : ChangeOutcome

    data class Skipped(override val path: String) : ChangeOutcome
}
