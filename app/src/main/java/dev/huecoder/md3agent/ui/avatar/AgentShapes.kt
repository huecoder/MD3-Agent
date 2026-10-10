package dev.huecoder.md3agent.ui.avatar

import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import dev.huecoder.md3agent.data.settings.AgentShape

val AgentShape.polygon: RoundedPolygon
    get() = when (this) {
        AgentShape.CIRCLE -> MaterialShapes.Circle
        AgentShape.HEXAGON -> RoundedHexagon
        AgentShape.SQUARE -> MaterialShapes.Square
        AgentShape.SLANTED -> MaterialShapes.Slanted
        AgentShape.ARCH -> MaterialShapes.Arch
        AgentShape.FAN -> MaterialShapes.Fan
        AgentShape.ARROW -> MaterialShapes.Arrow
        AgentShape.SEMI_CIRCLE -> MaterialShapes.SemiCircle
        AgentShape.OVAL -> MaterialShapes.Oval
        AgentShape.PILL -> MaterialShapes.Pill
        AgentShape.TRIANGLE -> MaterialShapes.Triangle
        AgentShape.DIAMOND -> MaterialShapes.Diamond
        AgentShape.CLAM_SHELL -> MaterialShapes.ClamShell
        AgentShape.PENTAGON -> MaterialShapes.Pentagon
        AgentShape.GEM -> MaterialShapes.Gem
        AgentShape.SUNNY -> MaterialShapes.Sunny
        AgentShape.VERY_SUNNY -> MaterialShapes.VerySunny
        AgentShape.COOKIE_4 -> MaterialShapes.Cookie4Sided
        AgentShape.COOKIE_6 -> MaterialShapes.Cookie6Sided
        AgentShape.COOKIE_7 -> MaterialShapes.Cookie7Sided
        AgentShape.COOKIE_9 -> MaterialShapes.Cookie9Sided
        AgentShape.COOKIE_12 -> MaterialShapes.Cookie12Sided
        AgentShape.GHOSTISH -> MaterialShapes.Ghostish
        AgentShape.CLOVER_4 -> MaterialShapes.Clover4Leaf
        AgentShape.CLOVER_8 -> MaterialShapes.Clover8Leaf
        AgentShape.BURST -> MaterialShapes.Burst
        AgentShape.SOFT_BURST -> MaterialShapes.SoftBurst
        AgentShape.BOOM -> MaterialShapes.Boom
        AgentShape.SOFT_BOOM -> MaterialShapes.SoftBoom
        AgentShape.FLOWER -> MaterialShapes.Flower
        AgentShape.PUFFY -> MaterialShapes.Puffy
        AgentShape.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond
        AgentShape.PIXEL_CIRCLE -> MaterialShapes.PixelCircle
        AgentShape.PIXEL_TRIANGLE -> MaterialShapes.PixelTriangle
        AgentShape.BUN -> MaterialShapes.Bun
        AgentShape.HEART -> MaterialShapes.Heart
    }

val AgentShape.symmetryDegrees: Float
    get() = when (this) {
        AgentShape.CIRCLE -> 0f
        AgentShape.HEXAGON, AgentShape.COOKIE_6 -> 60f
        AgentShape.SQUARE, AgentShape.COOKIE_4, AgentShape.CLOVER_4 -> 90f
        AgentShape.PENTAGON -> 72f
        AgentShape.COOKIE_7 -> FULL_TURN / 7f
        AgentShape.COOKIE_9 -> 40f
        AgentShape.COOKIE_12, AgentShape.BURST, AgentShape.SOFT_BURST -> 30f
        AgentShape.SUNNY, AgentShape.VERY_SUNNY, AgentShape.CLOVER_8, AgentShape.FLOWER -> 45f
        AgentShape.OVAL, AgentShape.PILL, AgentShape.DIAMOND -> 180f
        else -> FULL_TURN
    }

object AgentShapes {
    private const val FACE_SAFETY = 0.94f
    private const val MIN_FACE_RADIUS = 0.3f

    val faces: List<AgentShape> by lazy { AgentShape.entries.filter { faceRadius(it) != null } }

    fun faceRadius(shape: AgentShape): Float? =
        (BotGeometry.innerRadius(shape.polygon.cubics) * FACE_SAFETY).takeIf { it >= MIN_FACE_RADIUS }
}

private const val FULL_TURN = 360f
private const val HEXAGON_ROUNDING = 0.28f

private val RoundedHexagon: RoundedPolygon by lazy {
    RoundedPolygon(
        numVertices = 6,
        radius = BotGeometry.CENTER,
        centerX = BotGeometry.CENTER,
        centerY = BotGeometry.CENTER,
        rounding = CornerRounding(HEXAGON_ROUNDING),
    ).normalized()
}
