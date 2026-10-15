package dev.huecoder.md3agent.system.control

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.Display
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

sealed interface ScreenRegion {
    data object Full : ScreenRegion
    data class Area(val x: Int, val y: Int, val width: Int, val height: Int) : ScreenRegion
    data class Element(val number: Int) : ScreenRegion
}

sealed interface ScreenshotResult {
    class Captured(val jpeg: ByteArray, val region: Rect, val scale: Float) : ScreenshotResult
    data class Failed(val reason: String) : ScreenshotResult
}

internal class PhoneScreenshots {
    suspend fun capture(service: AccessibilityService, region: Rect?, marks: List<ScreenElement>): ScreenshotResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ScreenshotResult.Failed(NEEDS_ANDROID_11)
        val grabbed = when (val grab = grab(service)) {
            is Grab.Image -> grab.bitmap
            is Grab.Error -> return ScreenshotResult.Failed(failureText(grab.code))
            Grab.Empty -> return ScreenshotResult.Failed(EMPTY_SCREENSHOT)
        }
        try {
            val bounds = Rect(0, 0, grabbed.width, grabbed.height).also { whole -> region?.let { if (!whole.intersect(it)) whole.setEmpty() } }
            if (bounds.width() < MIN_SIDE_PX || bounds.height() < MIN_SIDE_PX) return ScreenshotResult.Failed(AREA_OUTSIDE_SCREEN)
            val scale = (MAX_SIDE_PX.toFloat() / maxOf(bounds.width(), bounds.height())).coerceAtMost(1f)
            val shown = Bitmap.createBitmap(
                (bounds.width() * scale).toInt().coerceAtLeast(1),
                (bounds.height() * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(shown)
            canvas.drawBitmap(grabbed, bounds, Rect(0, 0, shown.width, shown.height), Paint(Paint.FILTER_BITMAP_FLAG))
            if (marks.isNotEmpty()) drawMarks(canvas, shown.width, shown.height, marks, bounds, scale)
            val jpeg = ByteArrayOutputStream().also { shown.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
            shown.recycle()
            return ScreenshotResult.Captured(jpeg, bounds, scale)
        } finally {
            grabbed.recycle()
        }
    }

    private suspend fun grab(service: AccessibilityService, attempt: Int = 0): Grab {
        val grab = suspendCancellableCoroutine { continuation ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val hardware = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        screenshot.hardwareBuffer.close()
                        when {
                            !continuation.isActive -> software?.recycle()
                            software == null -> continuation.resume(Grab.Empty)
                            else -> continuation.resume(Grab.Image(software))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resume(Grab.Error(errorCode))
                    }
                },
            )
        }
        val tooSoon = grab is Grab.Error && grab.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
        if (!tooSoon || attempt >= MAX_RETRIES) return grab
        delay(RETRY_DELAY_MS)
        return grab(service, attempt + 1)
    }

    private fun failureText(code: Int): String = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "Phone control is not allowed to take screenshots"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Screenshots are limited to about one per second, try again"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "The display is not available for screenshots"
        ERROR_SECURE_WINDOW -> "The app on screen blocks screenshots (secure window), rely on phone_screen text instead"
        else -> "The system could not take a screenshot (code $code)"
    }

    private fun drawMarks(canvas: Canvas, width: Int, height: Int, elements: List<ScreenElement>, region: Rect, scale: Float) {
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = BOX_STROKE_PX
            color = MARK_COLOR
        }
        val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MARK_COLOR }
        val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = TAG_TEXT_PX
            isFakeBoldText = true
        }
        elements.filter { it.clickable || it.editable || it.scrollable }.forEach { element ->
            if (!Rect.intersects(element.bounds.toRect(), region)) return@forEach
            val rect = RectF(
                (element.bounds.left - region.left) * scale,
                (element.bounds.top - region.top) * scale,
                (element.bounds.right - region.left) * scale,
                (element.bounds.bottom - region.top) * scale,
            )
            canvas.drawRect(rect, box)
            val label = element.number.toString()
            val tagWidth = digits.measureText(label) + TAG_PADDING_PX * 2
            val tagHeight = TAG_TEXT_PX + TAG_PADDING_PX
            val left = rect.left.coerceIn(0f, (width - tagWidth).coerceAtLeast(0f))
            val top = rect.top.coerceIn(0f, (height - tagHeight).coerceAtLeast(0f))
            canvas.drawRoundRect(left, top, left + tagWidth, top + tagHeight, TAG_RADIUS_PX, TAG_RADIUS_PX, tag)
            canvas.drawText(label, left + TAG_PADDING_PX, top + TAG_TEXT_PX - TAG_PADDING_PX / 2, digits)
        }
    }

    private sealed interface Grab {
        class Image(val bitmap: Bitmap) : Grab
        data class Error(val code: Int) : Grab
        data object Empty : Grab
    }

    private companion object {
        const val NEEDS_ANDROID_11 = "Screenshots need Android 11 or newer, use phone_screen text instead"
        const val EMPTY_SCREENSHOT = "The system returned an empty screenshot"
        const val AREA_OUTSIDE_SCREEN = "The requested area is outside the screen or too small"
        const val ERROR_SECURE_WINDOW = 6
        const val MAX_SIDE_PX = 1600
        const val MIN_SIDE_PX = 8
        const val JPEG_QUALITY = 88
        const val MAX_RETRIES = 2
        const val RETRY_DELAY_MS = 1_100L
        const val BOX_STROKE_PX = 3f
        const val TAG_TEXT_PX = 26f
        const val TAG_PADDING_PX = 6f
        const val TAG_RADIUS_PX = 8f
        const val MARK_COLOR = 0xFFE53935.toInt()
    }
}
