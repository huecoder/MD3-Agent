package dev.huecoder.md3assistant.ui.avatar

import dev.huecoder.md3assistant.data.settings.AvatarRestShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
        assertEquals(-left.x, right.x, 1e-5f)
        assertEquals(left.y, right.y, 1e-5f)
        assertEquals(left.height, right.height, 1e-5f)
        assertTrue(left.y < 0f)
    }

    @Test
    fun `looking sideways squeezes the far eye`() {
        val (left, right) = BotGeometry.eyes(Gaze(Gaze.MAX_YAW, 0f), blink = 0f, wobble = 0f, safeRadius = 0.4f)
        assertTrue(right.width < left.width)
        assertTrue(right.x > 0f)
    }

    @Test
    fun `closed eyes squash into a short line`() {
        val (open, _) = BotGeometry.eyes(Gaze.Center, blink = 0f, wobble = 0f, safeRadius = 0.4f)
        val (closed, _) = BotGeometry.eyes(Gaze.Center, blink = 1f, wobble = 0f, safeRadius = 0.4f)
        assertTrue(closed.height < open.height / 3f)
        assertTrue(closed.width > open.width)
        assertTrue(closed.height > 0f)
    }

    @Test
    fun `real shapes keep the eyes inside through every morph frame`() {
        AvatarRestShape.entries.forEach { rest ->
            val shapes = BotShapes.of(rest)
            assertTrue("safe radius ${shapes.safeRadius}", shapes.safeRadius in 0.25f..0.5f)
            val morphs = shapes.working.indices.map(shapes::step) + shapes.working.indices.map(shapes::settle) + shapes.wake()
            morphs.forEach { morph ->
                (0..40).forEach { step ->
                    val inner = BotGeometry.innerRadius(morph.asCubics(step / 40f))
                    grid.forEach { dx ->
                        val (left, right) = BotGeometry.eyes(BotGeometry.gazeTowards(dx, dx / 2), blink = 0f, wobble = 1f, shapes.safeRadius)
                        assertTrue(left.reach <= inner && right.reach <= inner)
                        assertTrue(hypot(left.x, left.y) < inner)
                    }
                }
            }
        }
    }
}
