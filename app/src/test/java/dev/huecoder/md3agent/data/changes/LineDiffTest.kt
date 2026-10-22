package dev.huecoder.md3agent.data.changes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LineDiffTest {
    private fun diff(old: String, new: String) = LineDiff.compute(LineDiff.splitLines(old), LineDiff.splitLines(new))

    private fun List<DiffLine>.old() = filter { it.kind != DiffKind.ADDED }.map { it.text }

    private fun List<DiffLine>.new() = filter { it.kind != DiffKind.REMOVED }.map { it.text }

    @Test
    fun `identical texts have no changes`() {
        assertTrue(LineDiff.counts(diff("a\nb\n", "a\nb\n")).isEmpty)
    }

    @Test
    fun `new file is all additions`() {
        val counts = LineDiff.counts(diff("", "a\nb\nc\n"))
        assertEquals(DiffCounts(added = 3, removed = 0), counts)
    }

    @Test
    fun `deleted content is all removals`() {
        assertEquals(DiffCounts(added = 0, removed = 2), LineDiff.counts(diff("a\nb\n", "")))
    }

    @Test
    fun `a replaced line counts once each way`() {
        val lines = diff("a\nb\nc\n", "a\nB\nc\n")
        assertEquals(DiffCounts(added = 1, removed = 1), LineDiff.counts(lines))
        assertEquals(listOf(2, null), lines.filter { it.kind != DiffKind.SAME }.map { it.oldNumber })
    }

    @Test
    fun `line numbers follow both files`() {
        val lines = diff("a\nb\n", "x\na\nb\n")
        assertEquals(listOf(null, 1, 2), lines.map { it.oldNumber })
        assertEquals(listOf(1, 2, 3), lines.map { it.newNumber })
    }

    @Test
    fun `trailing newline does not add a line`() {
        assertEquals(listOf("a", "b"), LineDiff.splitLines("a\nb\n"))
        assertEquals(listOf("a", "b"), LineDiff.splitLines("a\nb"))
        assertEquals(listOf("a", "", "b"), LineDiff.splitLines("a\n\nb\n"))
    }

    @Test
    fun `carriage returns are not part of the line`() {
        assertEquals(listOf("a", "b"), LineDiff.splitLines("a\r\nb\r\n"))
    }

    @Test
    fun `unchanged stretches collapse around the context`() {
        val old = (1..40).joinToString("\n") { "line $it" }
        val new = old.replace("line 20", "changed")
        val rows = LineDiff.rows(diff(old, new), context = 3)
        val collapsed = rows.filterIsInstance<DiffRow.Collapsed>()
        assertEquals(listOf(16, 17), collapsed.map { it.count })
        assertEquals(2 * 3 + 2, rows.filterIsInstance<DiffRow.Line>().size)
    }

    @Test
    fun `an expanded stretch shows its lines`() {
        val old = (1..40).joinToString("\n") { "line $it" }
        val lines = diff(old, old.replace("line 20", "changed"))
        val first = LineDiff.rows(lines, context = 3).filterIsInstance<DiffRow.Collapsed>().first()
        val rows = LineDiff.rows(lines, context = 3, expanded = setOf(first.from))
        assertEquals(1, rows.filterIsInstance<DiffRow.Collapsed>().size)
    }

    @Test
    fun `no context shows everything`() {
        val lines = diff("a\nb\n", "a\nc\n")
        assertEquals(lines.size, LineDiff.rows(lines, context = null).size)
    }

    @Test
    fun `random edits always rebuild both sides`() {
        val random = Random(7)
        repeat(200) {
            val old = List(random.nextInt(0, 30)) { "l${random.nextInt(0, 8)}" }
            val new = List(random.nextInt(0, 30)) { "l${random.nextInt(0, 8)}" }
            val lines = LineDiff.compute(old, new)
            assertEquals(old, lines.old())
            assertEquals(new, lines.new())
        }
    }

    @Test
    fun `huge rewrites still produce a valid diff`() {
        val old = List(3_000) { "old $it" }
        val new = List(3_000) { "new $it" }
        val lines = LineDiff.compute(old, new)
        assertEquals(old, lines.old())
        assertEquals(new, lines.new())
    }
}
