package dev.huecoder.md3agent.system.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PhoneGestures {
    suspend fun tap(service: AccessibilityService, x: Int, y: Int): Boolean =
        dispatch(service, GestureDescription.Builder().addStroke(point(x, y, 0, TAP_MS)).build())

    suspend fun longPress(service: AccessibilityService, x: Int, y: Int): Boolean =
        dispatch(service, GestureDescription.Builder().addStroke(point(x, y, 0, LONG_PRESS_MS)).build())

    suspend fun doubleTap(service: AccessibilityService, x: Int, y: Int): Boolean =
        dispatch(
            service,
            GestureDescription.Builder()
                .addStroke(point(x, y, 0, TAP_MS))
                .addStroke(point(x, y, TAP_MS + DOUBLE_TAP_GAP_MS, TAP_MS))
                .build(),
        )

    suspend fun swipe(service: AccessibilityService, from: ScreenPoint, to: ScreenPoint, durationMs: Long): Boolean =
        dispatch(service, GestureDescription.Builder().addStroke(StrokeDescription(line(from, to), 0, durationMs)).build())

    suspend fun swipeAndHold(service: AccessibilityService, from: ScreenPoint, to: ScreenPoint, durationMs: Long): Boolean {
        val travel = StrokeDescription(line(from, to), 0, durationMs, true)
        if (!dispatch(service, GestureDescription.Builder().addStroke(travel).build())) return false
        val hold = travel.continueStroke(Path().apply { moveTo(to.x.toFloat(), to.y.toFloat()) }, 0, HOLD_MS, false)
        return dispatch(service, GestureDescription.Builder().addStroke(hold).build())
    }

    suspend fun path(service: AccessibilityService, points: List<ScreenPoint>, durationMs: Long): Boolean {
        val route = Path().apply {
            moveTo(points.first().x.toFloat(), points.first().y.toFloat())
            points.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
        }
        return dispatch(service, GestureDescription.Builder().addStroke(StrokeDescription(route, 0, durationMs)).build())
    }

    private fun point(x: Int, y: Int, startMs: Long, durationMs: Long) =
        StrokeDescription(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, startMs, durationMs)

    private fun line(from: ScreenPoint, to: ScreenPoint) = Path().apply {
        moveTo(from.x.toFloat(), from.y.toFloat())
        lineTo(to.x.toFloat(), to.y.toFloat())
    }

    private suspend fun dispatch(service: AccessibilityService, gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { continuation ->
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

    companion object {
        const val TAP_MS = 60L
        const val LONG_PRESS_MS = 700L
        const val DOUBLE_TAP_GAP_MS = 110L
        const val HOLD_MS = 140L
    }
}
