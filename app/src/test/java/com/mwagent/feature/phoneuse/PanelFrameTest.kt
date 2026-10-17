package com.mwagent.feature.phoneuse

import com.mwagent.data.settings.PanelSpot
import com.mwagent.data.settings.PhoneUseSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelFrameTest {
    private val settings = PhoneUseSettings()

    @Test
    fun `defaults are full opacity, three quarters wide and a third of the height`() {
        assertEquals(1f, settings.panelOpacity)
        assertEquals(0.75f, settings.panelWidth)
        assertEquals(0.3f, settings.panelHeight)
    }

    @Test
    fun `portrait width and log height follow the screen`() {
        val frame = PanelFrame.of(1080, 2400, settings)

        assertEquals(810, frame.widthPx)
        assertEquals(720, frame.logMaxHeightPx)
        assertFalse(frame.isLandscape)
    }

    @Test
    fun `landscape keeps the portrait width instead of stretching across the screen`() {
        val portrait = PanelFrame.of(1080, 2400, settings)
        val landscape = PanelFrame.of(2400, 1080, settings)

        assertEquals(portrait.widthPx, landscape.widthPx)
        assertTrue(landscape.isLandscape)
    }

    @Test
    fun `landscape log never takes more than its share of the short screen`() {
        val landscape = PanelFrame.of(2400, 1080, settings)

        assertEquals((1080 * PanelFrame.LANDSCAPE_LOG_SHARE).toInt(), landscape.logMaxHeightPx)
    }

    @Test
    fun `a small log stays as it is in landscape`() {
        val small = settings.copy(panelHeight = 0.15f)

        assertEquals(360, PanelFrame.of(1080, 2400, small).logMaxHeightPx)
        assertEquals(360, PanelFrame.of(2400, 1080, small).logMaxHeightPx)
    }

    @Test
    fun `landscape rests in the bottom end corner until the user moves it`() {
        val frame = PanelFrame.of(2400, 1080, settings)

        val spot = frame.restingSpot(2400, saved = null)

        assertEquals(frame.horizontalReach(2400) - PanelFrame.CORNER_MARGIN_PX, spot.x)
        assertEquals(0, spot.y)
        assertTrue(spot.x > 0)
    }

    @Test
    fun `portrait rests centered until the user moves it`() {
        val frame = PanelFrame.of(1080, 2400, settings)

        assertEquals(PanelSpot(0, 0), frame.restingSpot(1080, saved = null))
    }

    @Test
    fun `a saved spot wins but cannot leave the screen`() {
        val frame = PanelFrame.of(1080, 2400, settings)
        val reach = frame.horizontalReach(1080)

        assertEquals(PanelSpot(40, 300), frame.restingSpot(1080, PanelSpot(40, 300)))
        assertEquals(PanelSpot(reach, 300), frame.restingSpot(1080, PanelSpot(5000, 300)))
        assertEquals(PanelSpot(-reach, 0), frame.restingSpot(1080, PanelSpot(-5000, 0)))
    }
}
