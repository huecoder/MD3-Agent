package com.mwagent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class PanelSpot(val x: Int, val y: Int)

@Serializable
data class PhonePanelState(
    val collapsed: Boolean = false,
    val minimized: Boolean = false,
    val portraitSpot: PanelSpot? = null,
    val landscapeSpot: PanelSpot? = null,
    val bubbleAtEnd: Boolean = true,
    val bubbleHeightShare: Float = DEFAULT_BUBBLE_HEIGHT_SHARE,
) {
    fun spot(landscape: Boolean): PanelSpot? = if (landscape) landscapeSpot else portraitSpot

    fun withSpot(landscape: Boolean, spot: PanelSpot): PhonePanelState =
        if (landscape) copy(landscapeSpot = spot) else copy(portraitSpot = spot)

    companion object {
        const val DEFAULT_BUBBLE_HEIGHT_SHARE = 0.62f
    }
}
