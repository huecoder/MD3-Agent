package dev.huecoder.md3assistant.ui.avatar

import androidx.graphics.shapes.Cubic
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
    private const val EYE_HALF_LENGTH = 0.41f
    private const val EYE_RADIUS = 0.155f
    private const val EYE_SPREAD = 0.36f
    private const val EYE_LIFT = 0.16f
    private const val CLOSED_EYE = 0.14f
    private const val MAX_WOBBLE = 0.1f
    private const val BLINK_STRETCH = 0.25f
    private const val CAP_POINTS = 16
    private const val SIDE_POINTS = 12
    private const val EPSILON = 1e-4f
    private val FRONT = Vec3(0f, 0f, 1f)

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
        val radius = EYE_RADIUS * (1f + MAX_WOBBLE * wobble.coerceIn(-1f, 1f)) * (1f + BLINK_STRETCH * closing)
        val halfLength = EYE_HALF_LENGTH * (1f - (1f - CLOSED_EYE) * closing)
        val yaw = gaze.yaw.coerceIn(-Gaze.MAX_YAW, Gaze.MAX_YAW)
        val pitch = gaze.pitch.coerceIn(-Gaze.MAX_PITCH, Gaze.MAX_PITCH) + EYE_LIFT
        val left = project(sphericalCapsule(halfLength, radius), yaw - EYE_SPREAD, pitch, safeRadius)
        val right = project(sphericalCapsule(halfLength, radius), yaw + EYE_SPREAD, pitch, safeRadius)
        return left to right
    }

    fun gazeTowards(dx: Float, dy: Float): Gaze = Gaze(dx * Gaze.MAX_YAW, -dy * Gaze.MAX_PITCH)

    fun isInside(eye: Eye, safeRadius: Float) = eye.reach <= safeRadius + EPSILON

    private fun sphericalCapsule(halfLength: Float, radius: Float): List<Vec3> {
        val upright = halfLength >= radius
        val axisAngle = if (upright) 0f else PI.toFloat() / 2f
        val capRadius = min(halfLength, radius)
        val arc = max(halfLength, radius) - capRadius
        val along = Vec3(-sin(axisAngle), cos(axisAngle), 0f)
        val side = Vec3(cos(axisAngle), sin(axisAngle), 0f)
        val start = FRONT * cos(arc) - along * sin(arc)
        val end = FRONT * cos(arc) + along * sin(arc)
        val startOutward = -(FRONT * sin(arc) + along * cos(arc))
        val endOutward = along * cos(arc) - FRONT * sin(arc)
        val half = PI.toFloat()
        val rightSide = (0..SIDE_POINTS).map { step ->
            val t = -arc + 2f * arc * step / SIDE_POINTS
            arcPoint(t, along) * cos(capRadius) + side * sin(capRadius)
        }
        val endCap = (1 until CAP_POINTS).map { step ->
            val phi = half * step / CAP_POINTS
            end * cos(capRadius) + (side * cos(phi) + endOutward * sin(phi)) * sin(capRadius)
        }
        val leftSide = rightSide.map { it - side * (2f * sin(capRadius)) }.reversed()
        val startCap = (1 until CAP_POINTS).map { step ->
            val phi = half * step / CAP_POINTS
            start * cos(capRadius) + (-side * cos(phi) + startOutward * sin(phi)) * sin(capRadius)
        }
        return rightSide + endCap + leftSide + startCap
    }

    private fun arcPoint(t: Float, along: Vec3) = FRONT * cos(t) + along * sin(t)

    private fun project(points: List<Vec3>, yaw: Float, pitch: Float, radius: Float): Eye {
        val xs = FloatArray(points.size)
        val ys = FloatArray(points.size)
        points.forEachIndexed { index, point ->
            val liftedY = point.y * cos(pitch) - point.z * sin(pitch)
            val liftedZ = point.y * sin(pitch) + point.z * cos(pitch)
            xs[index] = (point.x * cos(yaw) + liftedZ * sin(yaw)) * radius
            ys[index] = liftedY * radius
        }
        return Eye(xs, ys)
    }

    private fun bezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1f - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
}
