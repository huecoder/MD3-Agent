package dev.huecoder.md3agent.system.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipePlannerTest {
    private val width = 1000
    private val height = 2000

    private fun plan(spec: SwipeSpec) = SwipePlanner.plan(spec, width, height)

    @Test
    fun `swiping up moves the finger from below the center to above it`() {
        val plan = plan(SwipeSpec(direction = SwipeDirection.UP))

        assertEquals(500, plan.from.x)
        assertEquals(500, plan.to.x)
        assertTrue(plan.from.y > 1000 && plan.to.y < 1000)
        assertEquals(1600, plan.from.y)
        assertEquals(400, plan.to.y)
        assertEquals(SwipePlanner.DEFAULT_MS, plan.durationMs)
    }

    @Test
    fun `swiping left moves the finger from the right to the left`() {
        val plan = plan(SwipeSpec(direction = SwipeDirection.LEFT))

        assertEquals(800, plan.from.x)
        assertEquals(200, plan.to.x)
        assertEquals(1000, plan.from.y)
    }

    @Test
    fun `a diagonal swipe moves on both axes`() {
        val plan = plan(SwipeSpec(direction = SwipeDirection.UP_RIGHT))

        assertTrue(plan.to.x > plan.from.x)
        assertTrue(plan.to.y < plan.from.y)
    }

    @Test
    fun `distance scales the swipe and stays off the screen edge`() {
        val short = plan(SwipeSpec(direction = SwipeDirection.DOWN, distance = 0.2f))
        val huge = plan(SwipeSpec(direction = SwipeDirection.DOWN, distance = 0.95f))

        assertEquals(400, short.to.y - short.from.y)
        assertTrue(huge.from.y >= SwipePlanner.EDGE_MARGIN_PX)
        assertTrue(huge.to.y <= height - SwipePlanner.EDGE_MARGIN_PX)
    }

    @Test
    fun `explicit points are kept but pulled inside the screen`() {
        val plan = plan(SwipeSpec(from = ScreenPoint(100, 1800), to = ScreenPoint(2000, -50), durationMs = 700))

        assertEquals(ScreenPoint(100, 1800), plan.from)
        assertEquals(ScreenPoint(999, 0), plan.to)
        assertEquals(700L, plan.durationMs)
    }

    @Test
    fun `a swipe inside a container stays inside it`() {
        val list = ScreenBox(100, 400, 900, 1600)

        val plan = SwipePlanner.inside(list, SwipeDirection.UP)

        assertEquals(500, plan.from.x)
        assertTrue(plan.from.y in 400..1600 && plan.to.y in 400..1600)
        assertTrue(plan.from.y > plan.to.y)
    }
}
