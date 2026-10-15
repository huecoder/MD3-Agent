package dev.huecoder.md3agent.feature.phoneuse

import kotlin.math.roundToInt

data class BubbleSpot(val x: Int, val y: Int)

object PanelBubble {
    fun spot(screenWidth: Int, screenHeight: Int, size: Int, margin: Int, atEnd: Boolean, heightShare: Float): BubbleSpot {
        val x = if (atEnd) screenWidth - size - margin else margin
        val lowest = (screenHeight - size - margin).coerceAtLeast(margin)
        val y = (screenHeight * heightShare).roundToInt().coerceIn(margin, lowest)
        return BubbleSpot(x, y)
    }

    fun clamped(spot: BubbleSpot, screenWidth: Int, screenHeight: Int, size: Int): BubbleSpot =
        BubbleSpot(spot.x.coerceIn(0, (screenWidth - size).coerceAtLeast(0)), spot.y.coerceIn(0, (screenHeight - size).coerceAtLeast(0)))

    fun staysAtEnd(left: Int, size: Int, screenWidth: Int): Boolean = left + size / 2 > screenWidth / 2

    fun heightShare(top: Int, screenHeight: Int): Float = (top.toFloat() / screenHeight).coerceIn(0f, 1f)
}
