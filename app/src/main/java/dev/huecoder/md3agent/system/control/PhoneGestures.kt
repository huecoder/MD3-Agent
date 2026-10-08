package dev.huecoder.md3agent.system.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PhoneGestures {
    suspend fun tap(service: AccessibilityService, x: Int, y: Int): Boolean =
        stroke(service, Path().apply { moveTo(x.toFloat(), y.toFloat()) }, TAP_MS)

    suspend fun longPress(service: AccessibilityService, x: Int, y: Int): Boolean =
        stroke(service, Path().apply { moveTo(x.toFloat(), y.toFloat()) }, LONG_PRESS_MS)

    suspend fun swipe(service: AccessibilityService, direction: SwipeDirection, screen: Point): Boolean {
        val centerX = screen.x / 2f
        val centerY = screen.y / 2f
        val reachX = screen.x * SWIPE_REACH / 2f
        val reachY = screen.y * SWIPE_REACH / 2f
        val (from, to) = when (direction) {
            SwipeDirection.UP -> (centerX to centerY + reachY) to (centerX to centerY - reachY)
            SwipeDirection.DOWN -> (centerX to centerY - reachY) to (centerX to centerY + reachY)
            SwipeDirection.LEFT -> (centerX + reachX to centerY) to (centerX - reachX to centerY)
            SwipeDirection.RIGHT -> (centerX - reachX to centerY) to (centerX + reachX to centerY)
        }
        val path = Path().apply {
            moveTo(from.first, from.second)
            lineTo(to.first, to.second)
        }
        return stroke(service, path, SWIPE_MS)
    }

    private suspend fun stroke(service: AccessibilityService, path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { continuation ->
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            if (!service.dispatchGesture(gesture, callback, null) && continuation.isActive) continuation.resume(false)
        }

    private companion object {
        const val TAP_MS = 60L
        const val LONG_PRESS_MS = 700L
        const val SWIPE_MS = 320L
        const val SWIPE_REACH = 0.6f
    }
}
