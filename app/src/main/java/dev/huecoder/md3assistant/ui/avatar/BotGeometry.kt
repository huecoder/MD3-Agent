package dev.huecoder.md3assistant.ui.avatar

import androidx.graphics.shapes.Cubic
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Eye(val x: Float, val y: Float, val width: Float, val height: Float, val tiltDegrees: Float) {
    val reach: Float get() = hypot(x, y) + max(width, height) / 2f
}

data class Gaze(val yaw: Float, val pitch: Float) {
    companion object {
        val Center = Gaze(0f, 0f)
        const val MAX_YAW = 0.6f
        const val MAX_PITCH = 0.4f
    }
}

object BotGeometry {
    const val CENTER = 0.5f
    private const val SAMPLES_PER_CUBIC = 24
    private const val EYE_HEIGHT = 0.32f
    private const val EYE_WIDTH = 0.125f
    private const val EYE_SPREAD = 0.6f
    private const val EYE_LIFT = 0.12f
    private const val BASE_TILT = 10f
    private const val TILT_PER_RADIAN = 8f
    private const val MIN_SPHERE = 0.12f
    private const val MIN_WIDTH_SCALE = 0.62f
    private const val MIN_HEIGHT_SCALE = 0.6f
    private const val CLOSED_EYE = 0.12f
    private const val MAX_WOBBLE = 0.12f
    private const val BLINK_STRETCH = 0.3f
    private const val CLOSED_LINE_RATIO = 0.55f

    fun innerRadius(cubics: List<Cubic>): Float {
        var nearest = Float.MAX_VALUE
        cubics.forEach { cubic ->
            for (step in 0..SAMPLES_PER_CUBIC) {
                val t = step / SAMPLES_PER_CUBIC.toFloat()
                val x = bezier(t, cubic.anchor0X, cubic.control0X, cubic.control1X, cubic.anchor1X)
                val y = bezier(t, cubic.anchor0Y, cubic.control0Y, cubic.control1Y, cubic.anchor1Y)
                nearest = min(nearest, hypot(x - CENTER, y - CENTER))
            }
        }
        return nearest
    }

    fun eyes(gaze: Gaze, blink: Float, wobble: Float, safeRadius: Float): Pair<Eye, Eye> {
        val scale = min(1f, safeRadius / (EYE_HEIGHT / 2f + MIN_SPHERE))
        val height = EYE_HEIGHT * scale
        val closing = blink.coerceIn(0f, 1f)
        val width = EYE_WIDTH * scale * (1f + MAX_WOBBLE * wobble.coerceIn(-1f, 1f)) * (1f + BLINK_STRETCH * closing)
        val sphere = safeRadius - height / 2f
        val yaw = gaze.yaw.coerceIn(-Gaze.MAX_YAW, Gaze.MAX_YAW)
        val pitch = (gaze.pitch.coerceIn(-Gaze.MAX_PITCH, Gaze.MAX_PITCH) + EYE_LIFT)
        val openness = 1f - (1f - CLOSED_EYE) * closing
        fun eye(eyeYaw: Float) = Eye(
            x = sphere * sin(eyeYaw) * cos(pitch),
            y = -sphere * sin(pitch),
            width = width * max(MIN_WIDTH_SCALE, cos(eyeYaw)),
            height = max(width * CLOSED_LINE_RATIO, height * max(MIN_HEIGHT_SCALE, cos(pitch)) * openness),
            tiltDegrees = BASE_TILT + eyeYaw * TILT_PER_RADIAN,
        )
        return eye(yaw - EYE_SPREAD) to eye(yaw + EYE_SPREAD)
    }

    fun gazeTowards(dx: Float, dy: Float): Gaze = Gaze(dx * Gaze.MAX_YAW, -dy * Gaze.MAX_PITCH)

    private fun bezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1f - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }

    fun isInside(eye: Eye, safeRadius: Float) = eye.reach <= safeRadius + EPSILON

    private const val EPSILON = 1e-4f
}
