package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneUseSettingsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tool settings saved before phone use existed still load with defaults`() {
        val loaded = json.decodeFromString(ToolSettings.serializer(), """{"permissions":{"phone_action":"ASK"}}""")
        assertEquals(PhoneUseSettings(), loaded.phoneUse)
    }

    @Test
    fun `phone use settings survive a round trip`() {
        val changed = PhoneUseSettings(touchGuard = TouchGuard.OBSERVE, tapSound = TapSound.MOUSE, agentPlacement = AgentPlacement.HIDDEN, cursorTrail = true)
        val raw = json.encodeToString(ToolSettings.serializer(), ToolSettings(phoneUse = changed))
        assertEquals(changed, json.decodeFromString(ToolSettings.serializer(), raw).phoneUse)
    }

    @Test
    fun `panel values nobody changed since the first build move to the new defaults`() {
        val stored = """{"phoneUse":{"panelOpacity":0.82,"panelWidth":0.94,"panelHeight":0.3,"cursorSize":1.4}}"""

        val loaded = json.decodeFromString(ToolSettings.serializer(), stored).migrated().phoneUse

        assertEquals(1f, loaded.panelOpacity)
        assertEquals(0.75f, loaded.panelWidth)
        assertEquals(1.4f, loaded.cursorSize)
    }

    @Test
    fun `panel values the user chose are kept`() {
        val stored = """{"phoneUse":{"panelOpacity":0.6,"panelWidth":0.94}}"""

        val loaded = json.decodeFromString(ToolSettings.serializer(), stored).migrated().phoneUse

        assertEquals(0.6f, loaded.panelOpacity)
        assertEquals(0.94f, loaded.panelWidth)
    }

    @Test
    fun `saved panel position from the first build is ignored`() {
        val stored = """{"phoneUse":{"panelCollapsed":true,"panelOffsetX":40,"panelOffsetY":300}}"""

        assertEquals(PhoneUseSettings(), json.decodeFromString(ToolSettings.serializer(), stored).phoneUse)
    }
}
