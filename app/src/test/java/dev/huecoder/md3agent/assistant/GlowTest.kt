package dev.huecoder.md3agent.assistant

import androidx.compose.ui.graphics.Color
import dev.huecoder.md3agent.assistant.glow.GlowEnvelope
import dev.huecoder.md3agent.assistant.glow.GlowLook
import dev.huecoder.md3agent.assistant.glow.GlowPalette
import dev.huecoder.md3agent.data.settings.AssistantGlow
import dev.huecoder.md3agent.data.settings.AssistantSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowTest {
    private val envelope = GlowEnvelope(fadeInSeconds = 1f, holdSeconds = 2f, fadeOutSeconds = 1f)

    @Test
    fun `the glow fades in, holds and fades out`() {
        assertEquals(0f, envelope.at(0f), 0f)
        assertEquals(0.5f, envelope.at(0.5f), 0.001f)
        assertEquals(1f, envelope.at(1f), 0f)
        assertEquals(1f, envelope.at(2.5f), 0f)
        assertEquals(0.5f, envelope.at(3.5f), 0.001f)
        assertEquals(0f, envelope.at(4f), 0f)
        assertEquals(0f, envelope.at(10f), 0f)
    }

    @Test
    fun `the glow lasts the sum of its stages`() {
        assertEquals(4f, envelope.durationSeconds, 0f)
    }

    @Test
    fun `accent palette keeps the hue of the theme colour`() {
        val green = Color(0xFF7FB069)
        val hue = GlowPalette.Oklch.hueOf(green)
        val rebuilt = GlowPalette.Oklch.hueOf(GlowPalette.Oklch.color(0.8f, 0.12f, hue))
        assertEquals(hue, rebuilt, 1f)
    }

    @Test
    fun `an achromatic accent still gets a hue`() {
        val hue = GlowPalette.Oklch.hueOf(Color.Gray)
        assertTrue(hue in 0f..360f)
    }

    @Test
    fun `palettes have between two and five stops`() {
        val accent = GlowPalette.stops(AssistantGlow.ACCENT, Color(0xFF7FB069))
        val rainbow = GlowPalette.stops(AssistantGlow.RAINBOW, Color(0xFF7FB069))
        assertTrue(accent.size in 2..GlowPalette.MAX_STOPS)
        assertEquals(GlowPalette.MAX_STOPS, rainbow.size)
    }

    @Test
    fun `the grid only adds dots to the look`() {
        val smooth = GlowLook.of(AssistantGlow.ACCENT, grid = false, thickness = 1f)
        val grid = GlowLook.of(AssistantGlow.ACCENT, grid = true, thickness = 1f)
        assertEquals(0f, smooth.dots, 0f)
        assertTrue(grid.dots > 0f)
        assertEquals(smooth, grid.copy(dots = 0f))
    }

    @Test
    fun `thickness is kept inside the range the settings allow`() {
        val thin = GlowLook.of(AssistantGlow.ACCENT, grid = false, thickness = 0f)
        val thick = GlowLook.of(AssistantGlow.ACCENT, grid = false, thickness = 9f)
        assertEquals(AssistantSettings.MIN_GLOW_THICKNESS, thin.thickness, 0f)
        assertEquals(AssistantSettings.MAX_GLOW_THICKNESS, thick.thickness, 0f)
    }

    @Test
    fun `a spanning envelope lasts the requested time and rises before it falls`() {
        val spanning = GlowEnvelope.spanning(2.4f)
        assertEquals(2.4f, spanning.durationSeconds, 0.001f)
        assertTrue(spanning.fadeInSeconds > 0f)
        assertTrue(spanning.holdSeconds > 0f)
        assertEquals(1f, spanning.at(spanning.fadeInSeconds + spanning.holdSeconds / 2), 0.001f)
        assertEquals(0f, spanning.at(2.4f), 0f)
    }

    @Test
    fun `a spanning envelope clamps the duration to the settings range`() {
        assertEquals(AssistantSettings.MIN_GLOW_SECONDS, GlowEnvelope.spanning(0f).durationSeconds, 0.001f)
        assertEquals(AssistantSettings.MAX_GLOW_SECONDS, GlowEnvelope.spanning(60f).durationSeconds, 0.001f)
    }
}
