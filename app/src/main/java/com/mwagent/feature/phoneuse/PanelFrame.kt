package com.mwagent.feature.phoneuse

import com.mwagent.data.settings.PanelSpot
import com.mwagent.data.settings.PhoneUseSettings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PanelFrame(val widthPx: Int, val logMaxHeightPx: Int, val isLandscape: Boolean) {
    fun horizontalReach(screenWidthPx: Int): Int = ((screenWidthPx - widthPx) / 2f).roundToInt().coerceAtLeast(0)

    fun cornerSpot(screenWidthPx: Int): PanelSpot = PanelSpot(horizontalReach(screenWidthPx) - CORNER_MARGIN_PX, 0)

    fun restingSpot(screenWidthPx: Int, saved: PanelSpot?): PanelSpot = when {
        saved != null -> saved.clampedTo(horizontalReach(screenWidthPx))
        isLandscape -> cornerSpot(screenWidthPx)
        else -> PanelSpot(0, 0)
    }

    private fun PanelSpot.clampedTo(reach: Int) = copy(x = x.coerceIn(-reach, reach))

    companion object {
        const val LANDSCAPE_LOG_SHARE = 0.4f
        const val CORNER_MARGIN_PX = 16

        fun of(screenWidthPx: Int, screenHeightPx: Int, settings: PhoneUseSettings): PanelFrame {
            val shortSide = min(screenWidthPx, screenHeightPx)
            val longSide = max(screenWidthPx, screenHeightPx)
            val isLandscape = screenWidthPx > screenHeightPx
            val portraitLog = longSide * settings.panelHeight
            val log = if (isLandscape) min(portraitLog, screenHeightPx * LANDSCAPE_LOG_SHARE) else portraitLog
            return PanelFrame((shortSide * settings.panelWidth).roundToInt(), log.roundToInt(), isLandscape)
        }
    }
}
