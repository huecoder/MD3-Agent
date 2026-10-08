package dev.huecoder.md3agent.ui.avatar

import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.huecoder.md3agent.data.settings.AvatarRestShape
import java.util.concurrent.ConcurrentHashMap

class BotShapes private constructor(restShape: AvatarRestShape) {
    val working: List<RoundedPolygon> = listOf(
        MaterialShapes.Cookie7Sided,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Gem,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.Cookie6Sided,
    )

    val rest: RoundedPolygon = when (restShape) {
        AvatarRestShape.CIRCLE -> MaterialShapes.Circle
        AvatarRestShape.HEXAGON -> ROUNDED_HEXAGON
    }

    val restSymmetryDegrees: Float = when (restShape) {
        AvatarRestShape.CIRCLE -> 0f
        AvatarRestShape.HEXAGON -> HEXAGON_SYMMETRY
    }

    private val steps: List<Morph> = working.indices.map { Morph(working[it], working[(it + 1) % working.size]) }
    private val settles: List<Morph> = working.map { Morph(it, rest) }
    private val wakes: Morph = Morph(rest, working.first())
    private val still: Morph = Morph(rest, rest)

    val safeRadius: Float = (steps + settles + wakes).minOf(::narrowest) * SAFETY

    fun step(index: Int): Morph = steps[index % steps.size]

    fun settle(fromIndex: Int): Morph = settles[fromIndex % settles.size]

    fun wake(): Morph = wakes

    fun still(): Morph = still

    private fun narrowest(morph: Morph): Float =
        (0..PROGRESS_SAMPLES).minOf { BotGeometry.innerRadius(morph.asCubics(it / PROGRESS_SAMPLES.toFloat())) }

    companion object {
        private val cache = ConcurrentHashMap<AvatarRestShape, BotShapes>()

        fun of(restShape: AvatarRestShape): BotShapes = cache.getOrPut(restShape) { BotShapes(restShape) }

        private const val PROGRESS_SAMPLES = 20
        private const val SAFETY = 0.94f
        private const val HEXAGON_SYMMETRY = 60f
        private const val HEXAGON_ROUNDING = 0.28f
        private val ROUNDED_HEXAGON: RoundedPolygon = RoundedPolygon(
            numVertices = 6,
            radius = BotGeometry.CENTER,
            centerX = BotGeometry.CENTER,
            centerY = BotGeometry.CENTER,
            rounding = CornerRounding(HEXAGON_ROUNDING),
        ).normalized()
    }
}
