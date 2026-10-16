package dev.huecoder.md3agent.feature.settings

import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.ui.components.ToolCategory
import dev.huecoder.md3agent.ui.components.ToolGroup
import dev.huecoder.md3agent.ui.components.group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolJumpBarTest {
    private val both = listOf(ToolGroup.BUILT_IN to -300f, ToolGroup.MCP to 1_200f)

    @Test
    fun `nothing is selected when there are no sections`() {
        assertNull(spiedGroup(emptyList(), atBottom = false, threshold = 100f))
    }

    @Test
    fun `the first group is selected until the next one reaches the top`() {
        assertEquals(ToolGroup.BUILT_IN, spiedGroup(both, atBottom = false, threshold = 100f))
    }

    @Test
    fun `a group becomes selected once its header passes the threshold`() {
        val scrolled = listOf(ToolGroup.BUILT_IN to -2_000f, ToolGroup.MCP to 60f)

        assertEquals(ToolGroup.MCP, spiedGroup(scrolled, atBottom = false, threshold = 100f))
    }

    @Test
    fun `at the very bottom the last group wins even if its header is still low`() {
        assertEquals(ToolGroup.MCP, spiedGroup(both, atBottom = true, threshold = 100f))
    }

    @Test
    fun `when the first header is below the threshold the first group is still selected`() {
        val atTop = listOf(ToolGroup.BUILT_IN to 220f, ToolGroup.MCP to 3_000f)

        assertEquals(ToolGroup.BUILT_IN, spiedGroup(atTop, atBottom = false, threshold = 100f))
    }

    @Test
    fun `only the MCP category belongs to the MCP group`() {
        assertEquals(ToolGroup.MCP, ToolCategory.MCP.group)
        assertEquals(setOf(ToolGroup.BUILT_IN), ToolCategory.entries.filter { it != ToolCategory.MCP }.map { it.group }.toSet())
    }

    @Test
    fun `MCP is listed last so each group is one block`() {
        assertEquals(ToolCategory.MCP, ToolCategory.entries.last())
    }

    @Test
    fun `a tool that was switched off remembers the mode it had`() {
        val modes = ToolModeMemory()

        modes.remember("tool", ToolPermission.ASK)

        assertEquals(ToolPermission.ASK, modes.modeOf("tool", ToolPermission.OFF, ToolPermission.AUTO))
        assertEquals(ToolPermission.AUTO, modes.modeOf("other", ToolPermission.OFF, ToolPermission.AUTO))
        assertEquals(ToolPermission.AUTO, modes.modeOf("tool", ToolPermission.AUTO, ToolPermission.ASK))
    }
}
