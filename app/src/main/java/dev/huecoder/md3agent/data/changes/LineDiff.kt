package dev.huecoder.md3agent.data.changes

import kotlin.math.min

enum class DiffKind { SAME, ADDED, REMOVED }

data class DiffLine(val kind: DiffKind, val text: String, val oldNumber: Int?, val newNumber: Int?)

sealed interface DiffRow {
    data class Line(val line: DiffLine) : DiffRow

    data class Collapsed(val from: Int, val count: Int) : DiffRow
}

data class DiffCounts(val added: Int, val removed: Int) {
    val isEmpty: Boolean get() = added == 0 && removed == 0
}

object LineDiff {
    private const val MAX_EDITS = 2_000

    fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = text.split('\n').map { it.removeSuffix("\r") }
        return if (text.endsWith('\n')) lines.dropLast(1) else lines
    }

    fun compute(old: List<String>, new: List<String>): List<DiffLine> {
        val prefix = commonPrefix(old, new)
        val suffix = commonSuffix(old, new, prefix)
        val oldMiddle = old.subList(prefix, old.size - suffix)
        val newMiddle = new.subList(prefix, new.size - suffix)
        val edits = if (oldMiddle.isEmpty() || newMiddle.isEmpty()) wholesale(oldMiddle, newMiddle) else myers(oldMiddle, newMiddle) ?: wholesale(oldMiddle, newMiddle)
        return numbered(old.subList(0, prefix), edits, old.subList(old.size - suffix, old.size))
    }

    fun counts(lines: List<DiffLine>) = DiffCounts(lines.count { it.kind == DiffKind.ADDED }, lines.count { it.kind == DiffKind.REMOVED })

    fun rows(lines: List<DiffLine>, context: Int?, expanded: Set<Int> = emptySet()): List<DiffRow> {
        if (context == null) return lines.map(DiffRow::Line)
        val visible = BooleanArray(lines.size)
        lines.forEachIndexed { index, line ->
            if (line.kind != DiffKind.SAME) {
                for (near in maxOf(0, index - context)..min(lines.lastIndex, index + context)) visible[near] = true
            }
        }
        val rows = mutableListOf<DiffRow>()
        var index = 0
        while (index < lines.size) {
            if (visible[index]) {
                rows += DiffRow.Line(lines[index])
                index++
                continue
            }
            val start = index
            while (index < lines.size && !visible[index]) index++
            if (start in expanded) {
                for (hidden in start until index) rows += DiffRow.Line(lines[hidden])
            } else {
                rows += DiffRow.Collapsed(start, index - start)
            }
        }
        return rows
    }

    private sealed interface Edit {
        data class Keep(val text: String) : Edit

        data class Add(val text: String) : Edit

        data class Remove(val text: String) : Edit
    }

    private fun commonPrefix(old: List<String>, new: List<String>): Int {
        var length = 0
        while (length < old.size && length < new.size && old[length] == new[length]) length++
        return length
    }

    private fun commonSuffix(old: List<String>, new: List<String>, prefix: Int): Int {
        var length = 0
        while (length < old.size - prefix && length < new.size - prefix && old[old.size - 1 - length] == new[new.size - 1 - length]) length++
        return length
    }

    private fun wholesale(old: List<String>, new: List<String>): List<Edit> = old.map(Edit::Remove) + new.map(Edit::Add)

    private fun numbered(head: List<String>, edits: List<Edit>, tail: List<String>): List<DiffLine> {
        val lines = ArrayList<DiffLine>(head.size + edits.size + tail.size)
        var oldNumber = 1
        var newNumber = 1
        head.forEach { lines += DiffLine(DiffKind.SAME, it, oldNumber++, newNumber++) }
        edits.forEach { edit ->
            lines += when (edit) {
                is Edit.Keep -> DiffLine(DiffKind.SAME, edit.text, oldNumber++, newNumber++)
                is Edit.Add -> DiffLine(DiffKind.ADDED, edit.text, null, newNumber++)
                is Edit.Remove -> DiffLine(DiffKind.REMOVED, edit.text, oldNumber++, null)
            }
        }
        tail.forEach { lines += DiffLine(DiffKind.SAME, it, oldNumber++, newNumber++) }
        return lines
    }

    private fun myers(a: List<String>, b: List<String>): List<Edit>? {
        val n = a.size
        val m = b.size
        val limit = min(n + m, MAX_EDITS)
        val offset = limit + 1
        val v = IntArray(2 * limit + 3)
        val trace = ArrayList<IntArray>()
        for (d in 0..limit) {
            trace += v.copyOf()
            for (k in -d..d step 2) {
                var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) v[offset + k + 1] else v[offset + k - 1] + 1
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) {
                    x++
                    y++
                }
                v[offset + k] = x
                if (x >= n && y >= m) return backtrack(trace, a, b, offset)
            }
        }
        return null
    }

    private fun backtrack(trace: List<IntArray>, a: List<String>, b: List<String>, offset: Int): List<Edit> {
        val edits = ArrayList<Edit>()
        var x = a.size
        var y = b.size
        for (d in trace.lastIndex downTo 0) {
            val v = trace[d]
            val k = x - y
            val previousK = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) k + 1 else k - 1
            val previousX = v[offset + previousK]
            val previousY = previousX - previousK
            while (x > previousX && y > previousY) {
                edits += Edit.Keep(a[x - 1])
                x--
                y--
            }
            if (d > 0) {
                if (x == previousX) edits += Edit.Add(b[y - 1]) else edits += Edit.Remove(a[x - 1])
            }
            x = previousX
            y = previousY
        }
        edits.reverse()
        return edits
    }
}
