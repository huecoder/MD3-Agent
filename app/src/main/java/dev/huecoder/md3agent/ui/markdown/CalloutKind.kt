package dev.huecoder.md3agent.ui.markdown

enum class CalloutKind(private val types: Set<String>) {
    NOTE(setOf("note")),
    INFO(setOf("info", "todo")),
    TIP(setOf("tip", "hint")),
    IMPORTANT(setOf("important")),
    WARNING(setOf("warning", "attention")),
    CAUTION(setOf("caution", "danger", "error", "bug", "failure", "fail", "missing")),
    SUCCESS(setOf("success", "check", "done")),
    QUESTION(setOf("question", "help", "faq")),
    EXAMPLE(setOf("example")),
    QUOTE(setOf("quote", "cite")),
    SUMMARY(setOf("abstract", "summary", "tldr")),
    ;

    companion object {
        fun of(type: String): CalloutKind? = entries.firstOrNull { type.lowercase() in it.types }
    }
}
