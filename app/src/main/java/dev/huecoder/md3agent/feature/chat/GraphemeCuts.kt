package dev.huecoder.md3agent.feature.chat

import java.text.BreakIterator

class GraphemeCuts {
    private val iterator: BreakIterator = BreakIterator.getCharacterInstance()

    fun floor(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        if (offset >= text.length) return text.length
        iterator.setText(text)
        return if (iterator.isBoundary(offset)) offset else iterator.preceding(offset)
    }

    fun forEach(text: String, start: Int, end: Int, action: (from: Int, to: Int) -> Unit) {
        iterator.setText(text)
        var from = start
        while (from < end) {
            val boundary = iterator.following(from)
            val to = if (boundary == BreakIterator.DONE) end else minOf(boundary, end)
            action(from, to)
            from = to
        }
    }
}
