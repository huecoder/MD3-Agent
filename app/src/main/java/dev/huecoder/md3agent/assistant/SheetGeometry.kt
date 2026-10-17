package dev.huecoder.md3agent.assistant

import androidx.compose.foundation.gestures.DraggableAnchors
import kotlin.math.max
import kotlin.math.min

enum class SheetValue { Hidden, Compact, Half, Full }

internal class SheetGeometry(
    val windowHeight: Float,
    private val halfHeight: Float,
    private val compactHeight: Float?,
) {
    val halfOffset: Float = windowHeight - halfHeight
    val compactOffset: Float? = compactHeight?.let { max(windowHeight - min(it, halfHeight), halfOffset + MIN_ANCHOR_GAP) }

    fun anchors(): DraggableAnchors<SheetValue> = DraggableAnchors {
        SheetValue.Full at 0f
        SheetValue.Half at halfOffset
        compactOffset?.let { SheetValue.Compact at it }
        SheetValue.Hidden at windowHeight
    }

    fun top(offset: Float, keyboard: Float): Float {
        val available = windowHeight - keyboard
        val half = halfTop(available)
        val position = offset.coerceIn(0f, windowHeight)
        val compact = compactOffset
        return when {
            position <= halfOffset -> interpolate(position, 0f, halfOffset, 0f, half)
            compact == null -> interpolate(position, halfOffset, windowHeight, half, windowHeight)
            position <= compact -> interpolate(position, halfOffset, compact, half, compactTop(available, half))
            else -> interpolate(position, compact, windowHeight, compactTop(available, half), windowHeight)
        }
    }

    fun chrome(offset: Float): Float {
        val compact = compactOffset ?: return 1f
        return ((compact - offset) / (compact - halfOffset)).coerceIn(0f, 1f)
    }

    fun openness(offset: Float): Float = (1f - offset / windowHeight).coerceIn(0f, 1f)

    private fun halfTop(available: Float): Float = available - min(halfHeight, available * MAX_HALF_OF_AVAILABLE)

    private fun compactTop(available: Float, half: Float): Float =
        max(available - min(compactHeight ?: 0f, available), half)

    private fun interpolate(x: Float, x0: Float, x1: Float, y0: Float, y1: Float): Float =
        if (x1 <= x0) y1 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)

    companion object {
        const val HALF_HEIGHT_FRACTION = 0.45f
        private const val MAX_HALF_OF_AVAILABLE = 0.7f
        private const val MIN_ANCHOR_GAP = 1f
    }
}
