package dev.huecoder.md3assistant.ui.components

object RussianPlural {
    fun of(count: Int, one: String, few: String, many: String): String {
        val lastTwo = count % 100
        val last = count % 10
        return when {
            lastTwo in 11..14 -> many
            last == 1 -> one
            last in 2..4 -> few
            else -> many
        }
    }
}
