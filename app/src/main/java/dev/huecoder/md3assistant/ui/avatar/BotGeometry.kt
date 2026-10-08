package dev.huecoder.md3assistant.ui.avatar

import androidx.graphics.shapes.Cubic
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Eye(val xs: FloatArray, val ys: FloatArray) {
    val reach: Float get() = xs.indices.maxOf { hypot(xs[it], ys[it]) }
    val width: Float get() = xs.max() - xs.min()
    val height: Float get() = ys.max() - ys.min()
    val centerX: Float get() = (xs.max() + xs.min()) / 2f
    val centerY: Float get() = (ys.max() + ys.min()) / 2f
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
    private const val EYE_HEIGHT = 0.86f
    private const val EYE_WIDTH = 0.32f
    private const val EYE_SPREAD = 0.36f
    private const val EYE_LIFT = 0.16f
    private const val TILT_RADIANS = 0.18f
    private const val CLOSED_EYE = 0.14f
    private const val MAX_WOBBLE = 0.1f
    private const val BLINK_STRETCH = 0.25f
    private const val CAP_POINTS = 14
    private const val EPSILON = 1e-4f

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
        val closing = blink.coerceIn(0f, 1f)
        val halfWidth = EYE_WIDTH / 2f * (1f + MAX_WOBBLE * wobble.coerceIn(-1f, 1f)) * (1f + BLINK_STRETCH * closing)
        val halfHeight = EYE_HEIGHT / 2f * (1f - (1f - CLOSED_EYE) * closing)
        val outline = capsule(halfWidth, halfHeight)
        val yaw = gaze.yaw.coerceIn(-Gaze.MAX_YAW, Gaze.MAX_YAW)
        val pitch = gaze.pitch.coerceIn(-Gaze.MAX_PITCH, Gaze.MAX_PITCH) + EYE_LIFT
        val left = place(tilted(outline, TILT_RADIANS), yaw - EYE_SPREAD, pitch, safeRadius)
        val right = place(tilted(outline, -TILT_RADIANS), yaw + EYE_SPREAD, pitch, safeRadius)
        return left to right
    }

    fun gazeTowards(dx: Float, dy: Float): Gaze = Gaze(dx * Gaze.MAX_YAW, -dy * Gaze.MAX_PITCH)

    fun isInside(eye: Eye, safeRadius: Float) = eye.reach <= safeRadius + EPSILON

    private fun capsule(halfWidth: Float, halfHeight: Float): List<Pair<Float, Float>> =
        if (halfHeight >= halfWidth) {
            uprightCapsule(halfWidth, halfHeight)
        } else {
            uprightCapsule(halfHeight, halfWidth).map { (u, v) -> v to u }
        }

    private fun uprightCapsule(halfWidth: Float, halfHeight: Float): List<Pair<Float, Float>> {
        val straight = halfHeight - halfWidth
        val half = PI.toFloat()
        val top = (0..CAP_POINTS).map { step ->
            val angle = half * step / CAP_POINTS
            halfWidth * cos(angle) to -straight - halfWidth * sin(angle)
        }
        val bottom = (0..CAP_POINTS).map { step ->
            val angle = half + half * step / CAP_POINTS
            halfWidth * cos(angle) to straight - halfWidth * sin(angle)
        }
        return top + bottom
    }

    private fun tilted(outline: List<Pair<Float, Float>>, radians: Float): List<Pair<Float, Float>> {
        val cosTilt = cos(radians)
        val sinTilt = sin(radians)
        return outline.map { (u, v) -> u * cosTilt - v * sinTilt to u * sinTilt + v * cosTilt }
    }

    private fun place(outline: List<Pair<Float, Float>>, yaw: Float, pitch: Float, radius: Float): Eye {
        val xs = FloatArray(outline.size)
        val ys = FloatArray(outline.size)
        outline.forEachIndexed { index, (u, v) ->
            val length = sqrt(u * u + v * v + 1f)
            val onSphereX = u / length
            val onSphereY = v / length
            val onSphereZ = 1f / length
            val liftedY = onSphereY * cos(pitch) - onSphereZ * sin(pitch)
            val liftedZ = onSphereY * sin(pitch) + onSphereZ * cos(pitch)
            xs[index] = (onSphereX * cos(yaw) + liftedZ * sin(yaw)) * radius
            ys[index] = liftedY * radius
        }
        return Eye(xs, ys)
    }

    private fun bezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1f - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }
}
