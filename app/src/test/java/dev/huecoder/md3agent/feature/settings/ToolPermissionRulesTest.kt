package dev.huecoder.md3agent.feature.settings

import dev.huecoder.md3agent.data.settings.ToolPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPermissionRulesTest {
    @Test
    fun `a blank query matches every tool`() {
        assertTrue(matchesSearch("", "Web search", "web_search"))
        assertTrue(matchesSearch("   ", "Web search"))
    }

    @Test
    fun `every word of the query has to be found`() {
        assertTrue(matchesSearch("web search", "Web", "Web search", "Searches and shows sources"))
        assertFalse(matchesSearch("web weather", "Web", "Web search", "Searches and shows sources"))
    }

    @Test
    fun `words may come from different fields and in any order`() {
        assertTrue(matchesSearch("pages web", "Web", "Reading pages", "Opens links"))
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(matchesSearch("PHONE", "Phone control", "Act on the phone"))
    }

    @Test
    fun `a tool that is on keeps its own mode`() {
        assertEquals(ToolPermission.ASK, modeBehind(ToolPermission.ASK, remembered = ToolPermission.AUTO, default = ToolPermission.AUTO))
        assertEquals(ToolPermission.AUTO, modeBehind(ToolPermission.AUTO, remembered = ToolPermission.ASK, default = ToolPermission.ASK))
    }

    @Test
    fun `a tool that is off comes back in the mode it had`() {
        assertEquals(ToolPermission.ASK, modeBehind(ToolPermission.OFF, remembered = ToolPermission.ASK, default = ToolPermission.AUTO))
    }

    @Test
    fun `a tool that is off and was never touched comes back in its default mode`() {
        assertEquals(ToolPermission.AUTO, modeBehind(ToolPermission.OFF, remembered = null, default = ToolPermission.AUTO))
        assertEquals(ToolPermission.ASK, modeBehind(ToolPermission.OFF, remembered = null, default = ToolPermission.ASK))
    }

    @Test
    fun `a tool that is off by default comes back asking`() {
        assertEquals(ToolPermission.ASK, modeBehind(ToolPermission.OFF, remembered = null, default = ToolPermission.OFF))
    }
}
