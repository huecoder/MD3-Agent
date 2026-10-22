package dev.huecoder.md3agent.ui.navigation

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

@Immutable
internal data class BackFrame(
    val closing: Rect,
    val entering: Rect,
    val cornerRadius: Float,
    val scrim: Float,
    val closingAlpha: Float,
) {
    companion object {
        val Rest = BackFrame(Rect.Zero, Rect.Zero, cornerRadius = 0f, scrim = 0f, closingAlpha = 1f)
    }
}

internal class BackEasings(val gesture: Easing, val commit: Easing)

internal class BackGeometry(
    width: Float,
    private val height: Float,
    private val startTouchY: Float,
    pressedToRight: Boolean,
    private val startRadius: Float,
    private val pxPerDp: Float,
    private val easings: BackEasings,
) {
    private val full = Rect(0f, 0f, width, height)
    private val closingEnd = closingEndRect(pressedToRight)
    private val enteringStart = enteringStartRect()
    private val enteringEnd = enteringStart.scaledAboutCenter(ENTERING_END_SCALE)
    private val endRadius = max(startRadius, RADIUS_END_DP * pxPerDp)

    fun gesture(progress: Float, touchY: Float): BackFrame {
        val eased = easings.gesture.transform(progress.coerceIn(0f, 1f))
        val closing = lerp(full, closingEnd, eased)
        val shiftY = verticalShift(closing.height, touchY)
        return BackFrame(
            closing = closing.translate(0f, shiftY),
            entering = lerp(enteringStart, enteringEnd, eased).translate(0f, shiftY),
            cornerRadius = lerp(startRadius, endRadius, eased),
            scrim = 1f,
            closingAlpha = 1f,
        )
    }

    fun commit(from: BackFrame, time: Float): BackFrame {
        val elapsed = time.coerceIn(0f, 1f)
        val eased = easings.commit.transform(elapsed)
        val closingExit = full.translate(from.closing.left + EXIT_SLIDE_DP * pxPerDp, 0f)
        return BackFrame(
            closing = lerp(from.closing, closingExit, eased),
            entering = lerp(from.entering, full, eased),
            cornerRadius = lerp(from.cornerRadius, startRadius, eased),
            scrim = from.scrim * (1f - elapsed),
            closingAlpha = max(1f - CLOSING_FADE_SPEED * elapsed, 0f),
        )
    }

    private fun closingEndRect(pressedToRight: Boolean): Rect {
        val shrunk = full.scaledAboutCenter(CLOSING_END_SCALE)
        return if (pressedToRight) shrunk.translate(full.right - shrunk.right - EDGE_MARGIN_DP * pxPerDp, 0f) else shrunk
    }

    private fun enteringStartRect(): Rect {
        val fitted = if (height > 0f) (height - 2f * startRadius) / height else ENTERING_START_MAX_SCALE
        val scaled = full.scaledAboutCenter(fitted.coerceIn(ENTERING_START_MIN_SCALE, ENTERING_START_MAX_SCALE))
        return scaled.translate(-max(scaled.width * ENTERING_SHIFT_FRACTION, ENTERING_SHIFT_MIN_DP * pxPerDp), 0f)
    }

    private fun verticalShift(currentHeight: Float, touchY: Float): Float {
        val half = height / 2f
        if (half <= 0f) return 0f
        val drag = touchY - startTouchY
        val reach = decelerate(min(half, abs(drag)) / half)
        return sign(drag) * reach * max(0f, (height - currentHeight) / 2f - EDGE_MARGIN_DP * pxPerDp)
    }

    private fun decelerate(fraction: Float) = 1f - (1f - fraction) * (1f - fraction)

    private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction
}

private fun Rect.scaledAboutCenter(scale: Float): Rect {
    val halfWidth = width * scale / 2f
    val halfHeight = height * scale / 2f
    return Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
}

private const val CLOSING_END_SCALE = 0.85f
private const val ENTERING_START_MIN_SCALE = 0.85f
private const val ENTERING_START_MAX_SCALE = 0.95f
private const val ENTERING_END_SCALE = 0.85f
private const val ENTERING_SHIFT_FRACTION = 0.15f
private const val ENTERING_SHIFT_MIN_DP = 96f
private const val EDGE_MARGIN_DP = 8f
private const val RADIUS_END_DP = 40f
private const val EXIT_SLIDE_DP = 96f
private const val CLOSING_FADE_SPEED = 5f
