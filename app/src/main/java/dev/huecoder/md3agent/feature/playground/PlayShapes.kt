package dev.huecoder.md3agent.feature.playground

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.huecoder.md3agent.ui.avatar.BotGeometry

class PlayShape private constructor(polygon: RoundedPolygon) {
    val outline: Path = Morph(polygon, polygon).toPath(0f).apply { translate(-CENTER_OFFSET) }
    val hull: List<Offset> = sampleOutline(polygon)
    val eyeRadius: Float? = (BotGeometry.innerRadius(polygon.cubics) * EYE_SAFETY).takeIf { it >= MIN_FACE_RADIUS }

    companion object {
        private const val SAMPLES_PER_CUBIC = 6
        private const val EYE_SAFETY = 0.94f
        private const val MIN_FACE_RADIUS = 0.3f
        private val CENTER_OFFSET = Offset(BotGeometry.CENTER, BotGeometry.CENTER)

        val all: List<PlayShape> by lazy {
            listOf(
                MaterialShapes.Circle, MaterialShapes.Square, MaterialShapes.Slanted, MaterialShapes.Arch,
                MaterialShapes.Fan, MaterialShapes.Arrow, MaterialShapes.SemiCircle, MaterialShapes.Oval,
                MaterialShapes.Pill, MaterialShapes.Triangle, MaterialShapes.Diamond, MaterialShapes.ClamShell,
                MaterialShapes.Pentagon, MaterialShapes.Gem, MaterialShapes.Sunny, MaterialShapes.VerySunny,
                MaterialShapes.Cookie4Sided, MaterialShapes.Cookie6Sided, MaterialShapes.Cookie7Sided,
                MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided, MaterialShapes.Ghostish,
                MaterialShapes.Clover4Leaf, MaterialShapes.Clover8Leaf, MaterialShapes.Burst, MaterialShapes.SoftBurst,
                MaterialShapes.Boom, MaterialShapes.SoftBoom, MaterialShapes.Flower, MaterialShapes.Puffy,
                MaterialShapes.PuffyDiamond, MaterialShapes.PixelCircle, MaterialShapes.PixelTriangle,
                MaterialShapes.Bun, MaterialShapes.Heart,
            ).map(::PlayShape)
        }

        val faces: List<PlayShape> by lazy { all.filter { it.eyeRadius != null } }

        private fun sampleOutline(polygon: RoundedPolygon): List<Offset> = polygon.cubics.flatMap { cubic ->
            (0 until SAMPLES_PER_CUBIC).map { step ->
                val t = step / SAMPLES_PER_CUBIC.toFloat()
                val u = 1f - t
                val x = u * u * u * cubic.anchor0X + 3 * u * u * t * cubic.control0X + 3 * u * t * t * cubic.control1X + t * t * t * cubic.anchor1X
                val y = u * u * u * cubic.anchor0Y + 3 * u * u * t * cubic.control0Y + 3 * u * t * t * cubic.control1Y + t * t * t * cubic.anchor1Y
                Offset(x, y) - CENTER_OFFSET
            }
        }
    }
}
