package dev.huecoder.md3agent.ui.avatar

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    val length: Float get() = sqrt(x * x + y * y + z * z)

    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun cross(other: Vec3) = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

    fun normalized(): Vec3 = length.let { if (it > 0f) this * (1f / it) else this }

    companion object {
        val Zero = Vec3(0f, 0f, 0f)
        val Right = Vec3(1f, 0f, 0f)
        val Down = Vec3(0f, 1f, 0f)
        val Front = Vec3(0f, 0f, 1f)
    }
}

data class Orientation(val w: Float, val x: Float, val y: Float, val z: Float) {
    operator fun times(other: Orientation) = Orientation(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
    )

    fun rotate(vector: Vec3): Vec3 {
        val axis = Vec3(x, y, z)
        val twice = (axis cross vector) * 2f
        return vector + twice * w + (axis cross twice)
    }

    fun spun(angularVelocity: Vec3, seconds: Float): Orientation {
        val speed = angularVelocity.length
        if (speed * seconds < EPSILON) return this
        return (around(angularVelocity * (1f / speed), speed * seconds) * this).normalized()
    }

    private fun normalized(): Orientation {
        val norm = sqrt(w * w + x * x + y * y + z * z)
        return Orientation(w / norm, x / norm, y / norm, z / norm)
    }

    companion object {
        private const val EPSILON = 1e-6f

        val Identity = Orientation(1f, 0f, 0f, 0f)

        fun around(axis: Vec3, radians: Float): Orientation {
            val unit = axis.normalized()
            val half = radians / 2f
            val s = sin(half)
            return Orientation(cos(half), unit.x * s, unit.y * s, unit.z * s)
        }
    }
}
