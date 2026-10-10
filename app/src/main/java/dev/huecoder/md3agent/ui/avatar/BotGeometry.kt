package dev.huecoder.md3agent.ui.avatar

import androidx.graphics.shapes.Cubic
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Outline(val xs: FloatArray, val ys: FloatArray) {
    val reach: Float get() = xs.indices.maxOf { hypot(xs[it], ys[it]) }
}

class Eye(val outlines: List<Outline>) {
    val reach: Float get() = outlines.maxOf { it.reach }
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (right + left) / 2f
    val centerY: Float get() = (bottom + top) / 2f

    private val left: Float get() = outlines.minOf { it.xs.min() }
    private val right: Float get() = outlines.maxOf { it.xs.max() }
    private val top: Float get() = outlines.minOf { it.ys.min() }
    private val bottom: Float get() = outlines.maxOf { it.ys.max() }
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
    private const val STROKE_RADIUS = 0.075f
    private const val MIN_SCALE = 0.01f
    private const val CAP_POINTS = 16
    private const val SIDE_POINTS = 12
    private const val EPSILON = 1e-4f
    private const val MIN_POLYGON = 3
    private val FRONT = Vec3.Front

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
        val yaw = gaze.yaw.coerceIn(-Gaze.MAX_YAW, Gaze.MAX_YAW)
        val pitch = gaze.pitch.coerceIn(-Gaze.MAX_PITCH, Gaze.MAX_PITCH) + EYE_LIFT
        val capsule = plainCapsule(blink, wobble, scale = 1f)
        val left = flatten(place(capsule, yaw - EYE_SPREAD, pitch), safeRadius)
        val right = flatten(place(capsule, yaw + EYE_SPREAD, pitch), safeRadius)
        return left to right
    }

    fun eyes(
        orientation: Orientation,
        blink: Float,
        safeRadius: Float,
        gaze: Gaze = Gaze.Center,
        wobble: Float = 0f,
        glyph: EyeGlyph = EyeGlyph.Plain,
        scale: Float = 1f,
    ): Pair<Eye?, Eye?> {
        if (scale < MIN_SCALE) return null to null
        val yaw = gaze.yaw.coerceIn(-Gaze.MAX_YAW, Gaze.MAX_YAW)
        val pitch = gaze.pitch.coerceIn(-Gaze.MAX_PITCH, Gaze.MAX_PITCH) + EYE_LIFT
        val leftShapes = glyphShapes(glyph, mirrored = false, blink, wobble, scale)
        val rightShapes = if (glyph == EyeGlyph.Plain) leftShapes else glyphShapes(glyph, mirrored = true, blink, wobble, scale)
        fun turned(shapes: List<List<Vec3>>, spread: Float): Eye? {
            val visible = shapes.mapNotNull { shape ->
                clipToFront(place(shape, yaw + spread, pitch).map(orientation::rotate)).takeIf { it.size >= MIN_POLYGON }
            }
            return if (visible.isEmpty()) null else Eye(visible.map { outline(it, safeRadius) })
        }
        return turned(leftShapes, -EYE_SPREAD) to turned(rightShapes, EYE_SPREAD)
    }

    fun gazeTowards(dx: Float, dy: Float): Gaze = Gaze(dx * Gaze.MAX_YAW, -dy * Gaze.MAX_PITCH)

    fun isInside(eye: Eye, safeRadius: Float) = eye.reach <= safeRadius + EPSILON

    private fun glyphShapes(glyph: EyeGlyph, mirrored: Boolean, blink: Float, wobble: Float, scale: Float): List<List<Vec3>> =
        if (glyph == EyeGlyph.Plain) {
            listOf(plainCapsule(blink, wobble, scale))
        } else {
            glyph.strokes(mirrored).map { strokeCapsule(it, scale) }
        }

    private fun plainCapsule(blink: Float, wobble: Float, scale: Float): List<Vec3> {
        val closing = blink.coerceIn(0f, 1f)
        val radius = EYE_RADIUS * (1f + MAX_WOBBLE * wobble.coerceIn(-1f, 1f)) * (1f + BLINK_STRETCH * closing)
        val halfLength = EYE_HALF_LENGTH * (1f - (1f - CLOSED_EYE) * closing)
        return sphericalCapsule(halfLength * scale, radius * scale)
    }

    private fun strokeCapsule(stroke: GlyphStroke, scale: Float): List<Vec3> {
        val dx = (stroke.endX - stroke.startX) * scale
        val dy = (stroke.endY - stroke.startY) * scale
        val radius = STROKE_RADIUS * scale
        val capsule = sphericalCapsule(hypot(dx, dy) / 2f + radius, radius)
        val centerX = (stroke.startX + stroke.endX) / 2f * scale
        val centerY = (stroke.startY + stroke.endY) / 2f * scale
        return place(rolled(capsule, atan2(-dx, dy)), centerX, -centerY)
    }

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

    private fun place(points: List<Vec3>, yaw: Float, pitch: Float): List<Vec3> = points.map { point ->
        val liftedY = point.y * cos(pitch) - point.z * sin(pitch)
        val liftedZ = point.y * sin(pitch) + point.z * cos(pitch)
        Vec3(point.x * cos(yaw) + liftedZ * sin(yaw), liftedY, liftedZ * cos(yaw) - point.x * sin(yaw))
    }

    private fun rolled(points: List<Vec3>, angle: Float): List<Vec3> {
        val cosine = cos(angle)
        val sine = sin(angle)
        return points.map { Vec3(it.x * cosine - it.y * sine, it.x * sine + it.y * cosine, it.z) }
    }

    private fun flatten(points: List<Vec3>, radius: Float): Eye = Eye(listOf(outline(points, radius)))

    private fun outline(points: List<Vec3>, radius: Float): Outline =
        Outline(FloatArray(points.size) { points[it].x * radius }, FloatArray(points.size) { points[it].y * radius })

    private fun clipToFront(points: List<Vec3>): List<Vec3> = buildList {
        points.indices.forEach { index ->
            val current = points[index]
            val next = points[(index + 1) % points.size]
            if (current.z >= 0f) add(current)
            if ((current.z >= 0f) != (next.z >= 0f)) {
                val t = current.z / (current.z - next.z)
                add(current + (next - current) * t)
            }
        }
    }

    private fun bezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1f - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }
}
