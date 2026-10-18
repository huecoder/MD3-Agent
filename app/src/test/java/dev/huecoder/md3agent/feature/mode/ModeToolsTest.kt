package dev.huecoder.md3agent.feature.mode

import dev.huecoder.md3agent.data.settings.ModePreset
import dev.huecoder.md3agent.data.settings.ModeSettings
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeToolsTest {
    private val everything = ModeSettings()
    private val everyday = everything.with(ModePreset.EVERYDAY, phoneControl = false)

    @Test
    fun `switching a group off silences its tools`() {
        val tools = ToolSettings().followingMode(everything, everyday)
        assertEquals(ToolPermission.OFF, tools.permissions["run_command"])
        assertEquals(ToolPermission.OFF, tools.permissions["run_python"])
        assertEquals(ToolPermission.OFF, tools.permissions["phone_action"])
    }

    @Test
    fun `a permission the user chose by hand survives switching off`() {
        val tools = ToolSettings().withPermission("run_command", ToolPermission.AUTO).followingMode(everything, everyday)
        assertEquals(ToolPermission.AUTO, tools.permissions["run_command"])
    }

    @Test
    fun `switching a group back on restores the defaults and keeps the rest`() {
        val off = ToolSettings().withPermission("web_search", ToolPermission.ASK).followingMode(everything, everyday)
        val back = off.followingMode(everyday, everything)
        assertNull(back.permissions["run_command"])
        assertNull(back.permissions["phone_screen"])
        assertEquals(ToolPermission.ASK, back.permissions["web_search"])
    }

    @Test
    fun `an unchanged mode leaves the tools as they were`() {
        val tools = ToolSettings().withPermission("run_command", ToolPermission.OFF)
        assertEquals(tools, tools.followingMode(everyday, everyday))
    }

    @Test
    fun `flags without tools do not touch permissions`() {
        val tools = ToolSettings()
        assertEquals(tools, tools.followingMode(everything, everything.copy(mcp = false, multipleAgents = false)))
    }
}
