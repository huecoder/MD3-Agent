package com.mwagent.ui.math

data class MathFormula(val latex: String, val isDisplay: Boolean)

class ExtractedMath(val markdown: String, val formulas: List<MathFormula>)

object MathExtractor {
    private const val SLOT_PREFIX = "MATHSLOT"
    private const val SLOT_SUFFIX = "X"
    private const val BACKTICK = '`'
    private const val ESCAPE = '\\'
    private const val DOLLAR = '$'
    private const val MAX_FENCE_INDENT = 3
    private val FENCES = listOf("```", "~~~")
    val slotPattern = Regex("$SLOT_PREFIX(\\d+)$SLOT_SUFFIX")

    fun slot(index: Int): String = "$SLOT_PREFIX$index$SLOT_SUFFIX"

    fun extract(source: String, isRenderable: (MathFormula) -> Boolean): ExtractedMath {
        if (DOLLAR !in source && "\\(" !in source && "\\[" !in source) return ExtractedMath(source, emptyList())
        return Scan(source, isRenderable).run()
    }

    private class Scan(private val source: String, private val isRenderable: (MathFormula) -> Boolean) {
        private val out = StringBuilder(source.length)
        private val formulas = mutableListOf<MathFormula>()
        private var cursor = 0

        fun run(): ExtractedMath {
            while (cursor < source.length) step()
            return ExtractedMath(out.toString(), formulas)
        }

        private fun step() {
            val fence = fenceAt(cursor)
            when {
                fence != null -> copyFencedBlock(fence)
                source[cursor] == BACKTICK -> copyCodeSpan()
                source[cursor] == ESCAPE -> escape()
                source[cursor] == DOLLAR -> dollar()
                else -> copy(1)
            }
        }

        private fun fenceAt(index: Int): String? {
            if (index > 0 && source[index - 1] != '\n') return null
            val indent = source.drop(index).takeWhile { it == ' ' }.length
            if (indent > MAX_FENCE_INDENT) return null
            return FENCES.firstOrNull { source.startsWith(it, index + indent) }
        }

        private fun copyFencedBlock(fence: String) {
            var lineStart = source.indexOf('\n', cursor).let { if (it < 0) source.length else it + 1 }
            while (lineStart < source.length) {
                val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it + 1 }
                val closes = source.substring(lineStart, lineEnd).trimStart().startsWith(fence)
                lineStart = lineEnd
                if (closes) break
            }
            copy(lineStart - cursor)
        }

        private fun copyCodeSpan() {
            val ticks = source.drop(cursor).takeWhile { it == BACKTICK }.length
            val marker = BACKTICK.toString().repeat(ticks)
            val close = source.indexOf(marker, cursor + ticks)
            copy(if (close < 0) ticks else close + ticks - cursor)
        }

        private fun escape() {
            val next = source.getOrNull(cursor + 1)
            when (next) {
                '(' -> delimited(open = "\\(", close = "\\)", isDisplay = false)
                '[' -> delimited(open = "\\[", close = "\\]", isDisplay = true)
                else -> copy(if (next == null) 1 else 2)
            }
        }

        private fun dollar() {
            if (source.startsWith("$$", cursor)) delimited(open = "$$", close = "$$", isDisplay = true) else inlineDollar()
        }

        private fun delimited(open: String, close: String, isDisplay: Boolean) {
            val end = source.indexOf(close, cursor + open.length)
            if (end < 0) return copy(open.length)
            emit(source.substring(cursor + open.length, end), isDisplay, consumed = end + close.length - cursor)
        }

        private fun inlineDollar() {
            val start = cursor + 1
            val end = closingDollar(start)
            if (end < 0) return copy(1)
            emit(source.substring(start, end), isDisplay = false, consumed = end + 1 - cursor)
        }

        private fun closingDollar(start: Int): Int {
            if (start >= source.length || source[start].isWhitespace()) return -1
            var index = start
            while (index < source.length && source[index] != '\n') {
                if (source[index] == ESCAPE) {
                    index += 2
                    continue
                }
                if (source[index] == DOLLAR) return if (closesInline(start, index)) index else -1
                index++
            }
            return -1
        }

        private fun closesInline(start: Int, index: Int): Boolean =
            index > start && !source[index - 1].isWhitespace() && source.getOrNull(index + 1)?.isDigit() != true

        private fun emit(latex: String, isDisplay: Boolean, consumed: Int) {
            val formula = MathFormula(latex.trim(), isDisplay)
            if (formula.latex.isNotEmpty() && isRenderable(formula)) {
                out.append(slot(formulas.size))
                formulas += formula
                cursor += consumed
            } else {
                copy(consumed)
            }
        }

        private fun copy(length: Int) {
            out.append(source, cursor, cursor + length)
            cursor += length
        }
    }
}
