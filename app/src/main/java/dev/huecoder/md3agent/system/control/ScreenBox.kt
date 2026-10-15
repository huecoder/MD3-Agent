package dev.huecoder.md3agent.system.control

import android.graphics.Rect

data class ScreenBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun intersects(other: ScreenBox): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun toRect(): Rect = Rect(left, top, right, bottom)

    companion object {
        fun of(rect: Rect) = ScreenBox(rect.left, rect.top, rect.right, rect.bottom)
    }
}
