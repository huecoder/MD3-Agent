package dev.huecoder.md3agent.ui.avatar

import dev.huecoder.md3agent.data.settings.AgentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class BotGeometryTest {
    private val grid = (-10..10).map { it / 10f }

    @Test
    fun `eyes stay inside the safe circle for every gaze blink and wobble`() {
        listOf(0.25f, 0.33f, 0.4f, 0.47f).forEach { safe ->
            grid.forEach { dx ->
                grid.forEach { dy ->
                    listOf(0f, 0.5f, 1f).forEach { blink ->
                        listOf(-1f, 0f, 1f).forEach { wobble ->
                            val (left, right) = BotGeometry.eyes(BotGeometry.gazeTowards(dx * 2, dy * 2), blink, wobble, safe)
                            assertTrue("left $left at $safe", BotGeometry.isInside(left, safe))
                            assertTrue("right $right at $safe", BotGeometry.isInside(right, safe))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `resting eyes look straight ahead and mirror each other`() {
        val (left, right) = BotGeometry.eyes(Gaze.Center, blink = 0f, wobble = 0f, safeRadius = 0.4f)
        assertEquals(-left.centerX, right.centerX, 1e-4f)
        assertEquals(left.centerY, right.centerY, 1e-4f)
        assertEquals(left.height, right.height, 1e-4f)
        assertTrue(left.centerY < 0f)
        assertTrue(left.height > left.width * 1.5f)
    }

    @Test
    fun `looking sideways squeezes the far eye`() {
        val (left, right) = BotGeometry.eyes(Gaze(Gaze.MAX_YAW, 0f), blink = 0f, wobble = 0f, safeRadius = 0.4f)
        assertTrue(right.width < left.width)
        assertTrue(right.centerX > 0f)
    }

    @Test
    fun `closed eyes squash into a short line`() {
        val (open, _) = BotGeometry.eyes(Gaze.Center, blink = 0f, wobble = 0f, safeRadius = 0.4f)
        val (closed, _) = BotGeometry.eyes(Gaze.Center, blink = 1f, wobble = 0f, safeRadius = 0.4f)
        assertTrue(closed.height < open.height / 3f)
        assertTrue(closed.width > closed.height * 2f)
        assertTrue(closed.height > 0f)
    }

    @Test
    fun `real shapes keep the eyes inside through every morph frame`() {
        AgentShapes.faces.forEach { rest ->
            val shapes = BotShapes.of(rest, AgentProfile.DEFAULT_WORKING_SHAPES)
            assertTrue("$rest safe radius ${shapes.safeRadius}", shapes.safeRadius in 0.25f..0.5f)
            val morphs = shapes.working.indices.map(shapes::step) + shapes.working.indices.map(shapes::settle) + shapes.wake()
            morphs.forEach { morph ->
                (0..40).forEach { step ->
                    val inner = BotGeometry.innerRadius(morph.asCubics(step / 40f))
                    grid.forEach { dx ->
                        val (left, right) = BotGeometry.eyes(BotGeometry.gazeTowards(dx, dx / 2), blink = 0f, wobble = 1f, shapes.safeRadius)
                        assertTrue(left.reach <= inner && right.reach <= inner)
                        assertTrue(hypot(left.centerX, left.centerY) < inner)
                    }
                }
            }
        }
    }

    @Test
    fun `upright orientation draws the same eyes as a straight gaze`() {
        val (left, right) = BotGeometry.eyes(Orientation.Identity, blink = 0f, safeRadius = 0.4f)
        val (straightLeft, straightRight) = BotGeometry.eyes(Gaze.Center, blink = 0f, wobble = 0f, safeRadius = 0.4f)
        assertEquals(straightLeft.centerX, left!!.centerX, 1e-4f)
        assertEquals(straightLeft.centerY, left.centerY, 1e-4f)
        assertEquals(straightRight.height, right!!.height, 1e-4f)
    }

    @Test
    fun `eyes hide behind the sphere when it turns its back`() {
        val (left, right) = BotGeometry.eyes(Orientation.around(Vec3(0f, 1f, 0f), PI.toFloat()), blink = 0f, safeRadius = 0.4f)
        assertNull(left)
        assertNull(right)
    }

    @Test
    fun `half turned sphere keeps the near eye and clips it to the rim`() {
        val (left, right) = BotGeometry.eyes(Orientation.around(Vec3(0f, 1f, 0f), 1.35f), blink = 0f, safeRadius = 0.4f)
        val visible = listOfNotNull(left, right)
        assertTrue(visible.isNotEmpty())
        visible.forEach { assertTrue(BotGeometry.isInside(it, 0.4f)) }
    }
}
