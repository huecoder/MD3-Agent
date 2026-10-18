package dev.huecoder.md3agent.ui.physics

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.avatar.polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PhysicsWorldOutlineTest {
    private val size = 400f
    private val ballRadius = 12f

    private fun world() = PhysicsWorld(PIXELS_PER_METER, gravity = 0.0, stepSeconds = STEP_SECONDS)

    private fun scaled(outline: List<Offset>) = outline.map { it * size }

    @Test
    fun `a ball flung at a shape touches its contour and not its bounding box`() {
        val shapes = listOf(AgentShape.COOKIE_12, AgentShape.CLOVER_4, AgentShape.SUNNY, AgentShape.BURST, AgentShape.FLOWER, AgentShape.HEART, AgentShape.PUFFY)
        shapes.forEach { shape ->
            val contour = scaled(ShapeParts.outline(shape.polygon))
            DIRECTIONS.forEach { degrees ->
                val world = world()
                world.addOutline(Offset.Zero, ShapeParts.of(shape), size, Material.Soft, isStatic = true)
                val direction = Offset(cos(degrees * PI / 180).toFloat(), sin(degrees * PI / 180).toFloat())
                val ball = world.addBall(direction * size, ballRadius, Material.Soft)
                world.setVelocity(ball, direction * -FLING_SPEED)
                val impacts = mutableListOf<Impact>()
                world.onImpact { impacts += it }
                repeat(MAX_FRAMES) { if (impacts.isEmpty()) world.step(FRAME_SECONDS) }
                val touched = checkNotNull(impacts.firstOrNull()) { "$shape from $degrees never hit" }.point
                val gap = distanceToOutline(touched, contour)
                assertTrue("$shape from $degrees touched ${gap}px away from the contour", gap < CONTACT_TOLERANCE)
            }
        }
    }

    @Test
    fun `a ball that fits in a notch rests there without a single contact`() {
        val outline = ShapeParts.outline(AgentShape.CLOVER_4.polygon)
        val hull = convexHull(outline)
        val notch = (-30..30).flatMap { x -> (-30..30).map { y -> Offset(x / 60f, y / 60f) } }
            .filter { insideConvex(it, hull) && !insideOutline(it, outline) }
            .maxBy { distanceToOutline(it, outline) }
        val room = distanceToOutline(notch, outline) * size
        val world = world()
        world.addOutline(Offset.Zero, ShapeParts.of(AgentShape.CLOVER_4), size, Material.Soft, isStatic = true)
        val ball = world.addBall(notch * size, room * FIT, Material.Soft)
        var contacts = 0
        world.onImpact { contacts++ }
        repeat(MAX_FRAMES) { world.step(FRAME_SECONDS) }
        assertEquals(0, contacts)
        assertEquals(0f, (world.position(ball) - notch * size).getDistance(), 0.01f)
    }

    @Test
    fun `swapping the outline moves what can be hit`() {
        val world = world()
        val body = world.addOutline(Offset.Zero, ShapeParts.of(AgentShape.CIRCLE), size, Material.Soft)
        val corner = Offset(size * 0.38f, size * 0.38f)
        assertNull(world.bodyAt(corner))
        world.replaceOutline(body, ShapeParts.of(AgentShape.SQUARE), size, Material.Soft)
        assertEquals(body, world.bodyAt(corner))
    }

    @Test
    fun `after the outline is swapped a ball meets the new contour`() {
        val world = world()
        val body = world.addOutline(Offset.Zero, ShapeParts.of(AgentShape.CIRCLE), size, Material.Soft, isStatic = true)
        world.replaceOutline(body, ShapeParts.of(AgentShape.CLOVER_4), size, Material.Soft)
        val contour = scaled(ShapeParts.outline(AgentShape.CLOVER_4.polygon))
        val direction = Offset(cos(PI / 4).toFloat(), sin(PI / 4).toFloat())
        val ball = world.addBall(direction * size, ballRadius, Material.Soft)
        world.setVelocity(ball, direction * -FLING_SPEED)
        val impacts = mutableListOf<Impact>()
        world.onImpact { impacts += it }
        repeat(MAX_FRAMES) { if (impacts.isEmpty()) world.step(FRAME_SECONDS) }
        val touched = checkNotNull(impacts.firstOrNull()) { "the ball never hit" }.point
        assertTrue(distanceToOutline(touched, contour) < CONTACT_TOLERANCE)
    }

    @Test
    fun `a body placed somewhere else is hit where it now stands`() {
        val world = world()
        val body = world.addOutline(Offset.Zero, ShapeParts.of(AgentShape.CLOVER_4), size, Material.Soft, isStatic = true)
        val center = Offset(900f, -300f)
        val turn = PI / 6
        world.place(body, center, turn)
        val contour = scaled(ShapeParts.outline(AgentShape.CLOVER_4.polygon)).map { corner ->
            val turned = Offset((corner.x * cos(turn) - corner.y * sin(turn)).toFloat(), (corner.x * sin(turn) + corner.y * cos(turn)).toFloat())
            turned + center
        }
        val direction = Offset(cos(PI / 3).toFloat(), sin(PI / 3).toFloat())
        val ball = world.addBall(center + direction * size, ballRadius, Material.Soft)
        world.setVelocity(ball, direction * -FLING_SPEED)
        val impacts = mutableListOf<Impact>()
        world.onImpact { impacts += it }
        repeat(MAX_FRAMES) { if (impacts.isEmpty()) world.step(FRAME_SECONDS) }
        val touched = checkNotNull(impacts.firstOrNull()) { "the ball never hit" }.point
        assertTrue(distanceToOutline(touched, contour) < CONTACT_TOLERANCE)
    }

    private companion object {
        const val PIXELS_PER_METER = 100f
        const val STEP_SECONDS = 1.0 / 240.0
        const val FRAME_SECONDS = 1f / 60f
        const val MAX_FRAMES = 240
        const val FLING_SPEED = 700f
        const val CONTACT_TOLERANCE = 4f
        const val FIT = 0.85f
        val DIRECTIONS = listOf(0, 30, 45, 90, 135, 180, 225, 270, 315)
    }
}
