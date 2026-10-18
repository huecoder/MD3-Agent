package dev.huecoder.md3agent.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeSettingsTest {
    @Test
    fun `a user who never saw the onboarding keeps every feature on`() {
        val fresh = ModeSettings()
        assertFalse(fresh.onboarded)
        assertEquals(ModePreset.DEVELOPER, fresh.preset)
    }

    @Test
    fun `every preset is recognised after it is applied`() {
        ModePreset.entries.forEach { preset ->
            assertEquals(preset, ModeSettings().with(preset, phoneControl = false).preset)
            assertEquals(preset, ModeSettings().with(preset, phoneControl = true).preset)
        }
    }

    @Test
    fun `the everyday preset keeps phone control only when it was asked for`() {
        assertFalse(ModeSettings().with(ModePreset.EVERYDAY, phoneControl = false).phoneControl)
        assertTrue(ModeSettings().with(ModePreset.EVERYDAY, phoneControl = true).phoneControl)
        assertTrue(ModeSettings().with(ModePreset.DEVELOPER, phoneControl = false).phoneControl)
    }

    @Test
    fun `applying a preset does not touch the onboarding mark`() {
        assertTrue(ModeSettings(onboarded = true).with(ModePreset.EVERYDAY, phoneControl = false).onboarded)
    }

    @Test
    fun `a mix that matches no preset has no name`() {
        assertNull(ModeSettings(shellAndFiles = false).preset)
        assertNull(ModeSettings(phoneControl = false).preset)
        assertNull(ModeSettings(shellAndFiles = false, multipleAgents = false).preset)
    }
}
