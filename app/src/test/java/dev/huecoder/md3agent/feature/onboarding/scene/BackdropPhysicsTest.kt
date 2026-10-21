package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.avatar.polygon
import dev.huecoder.md3agent.ui.physics.ShapeParts
import dev.huecoder.md3agent.ui.physics.distanceToOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BackdropPhysicsTest {
    private val stage = SceneStage(1080f, 2400f)
    private val fall = (stage.bottomCenter - stage.home).let { it / it.getDistance() }

    private class Jump(val time: Float, val hit: BackdropPhysics.Hit)

    private fun physics() = BackdropPhysics(stage, ScenePlan.topShape, ScenePlan.bottomShapes.first())

    private fun simulate(seconds: Float, engine: BackdropPhysics = physics(), onFrame: (BackdropPhysics, Float) -> Unit = { _, _ -> }): List<Jump> {
        val jumps = ArrayList<Jump>()
        var time = 0f
        while (time < seconds) {
            engine.step(FRAME) { jumps += Jump(time, it) }
            onFrame(engine, time)
            time += FRAME
        }
        return jumps
    }

    private fun contour(shape: AgentShape, size: Float, center: Offset, turn: Float): List<Offset> =
        ShapeParts.outline(shape.polygon).map { corner ->
            val x = corner.x * size
            val y = corner.y * size
            Offset(x * cos(turn) - y * sin(turn), x * sin(turn) + y * cos(turn)) + center
        }

    private fun assertTouches(shape: AgentShape, jump: Jump) {
        val bottom = contour(shape, stage.bottomSize, stage.bottomCenter, jump.hit.bottomAngle)
        val top = contour(ScenePlan.topShape, stage.topSize, jump.hit.topCenter, 0f)
        assertTrue("${distanceToOutline(jump.hit.point, bottom)}px from the bottom", distanceToOutline(jump.hit.point, bottom) < TOUCH_TOLERANCE)
        assertTrue("${distanceToOutline(jump.hit.point, top)}px from the top", distanceToOutline(jump.hit.point, top) < TOUCH_TOLERANCE)
    }

    @Test
    fun `every jump looks the same and is a single hit`() {
        val jumps = simulate(HALF_MINUTE)
        assertTrue("only ${jumps.size} jumps", jumps.size >= MIN_JUMPS)
        val gaps = jumps.zipWithNext { first, second -> second.time - first.time }
        assertTrue("a second hit came ${gaps.min()}s after the first", gaps.min() > MIN_GAP)
        assertTrue("jumps are ${gaps.max() - gaps.min()}s out of step", gaps.max() - gaps.min() < MAX_GAP_SPREAD)
        val speeds = jumps.map { it.hit.metersPerSecond }
        assertTrue("hit speeds differ ${speeds.max() / speeds.min()} times", speeds.max() / speeds.min() < MAX_SPEED_RATIO)
    }

    @Test
    fun `the top shape is back home and still before every next jump`() {
        val frames = ArrayList<Pair<Float, Offset>>()
        val jumps = simulate(HALF_MINUTE) { engine, time -> frames += time to engine.topCenter }
        val resting = frames.filter { (time, _) ->
            jumps.any { time > it.time + SceneMotion.RETURN_SECONDS + SETTLE_MARGIN && time < it.time + SceneMotion.RETURN_SECONDS + SETTLE_MARGIN + SETTLE_WINDOW }
        }
        assertTrue(resting.isNotEmpty())
        resting.forEach { (time, center) -> assertEquals("at ${time}s", 0f, (center - stage.home).getDistance(), HOME_TOLERANCE) }
    }

    @Test
    fun `the top shape stays on its line`() {
        simulate(HALF_MINUTE) { engine, _ ->
            val offset = engine.topCenter - stage.home
            val along = offset.x * fall.x + offset.y * fall.y
            val lateral = (offset - fall * along).getDistance()
            assertTrue("drifted ${lateral}px off the line", lateral < LINE_TOLERANCE)
            assertTrue("went ${along}px along the line", along > -LINE_TOLERANCE && along < stage.base * REACH)
        }
    }

    @Test
    fun `every hit lies on the contours of both shapes however the bottom one is turned`() {
        val jumps = simulate(HALF_MINUTE)
        assertTrue(jumps.map { it.hit.bottomAngle }.distinct().size > 1)
        jumps.forEach { assertTouches(ScenePlan.bottomShapes.first(), it) }
    }

    @Test
    fun `the bottom shape turns slowly and never stops or reverses`() {
        var previous = 0f
        val engine = physics()
        simulate(TURN_SECONDS, engine) { turned, _ ->
            assertTrue(turned.bottomAngle >= previous)
            previous = turned.bottomAngle
        }
        assertEquals(SceneMotion.BOTTOM_TURN_RADIANS_PER_SECOND * TURN_SECONDS, engine.bottomAngle, TURN_TOLERANCE)
    }

    @Test
    fun `after the bottom shape is swapped the next hit lands on the new contour`() {
        val engine = physics()
        assertTouches(ScenePlan.bottomShapes.first(), nextHit(engine))
        val next = ScenePlan.bottomShapes[1]
        engine.reshapeBottom(next)
        assertTouches(next, nextHit(engine))
    }

    private fun nextHit(engine: BackdropPhysics): Jump {
        var hit: BackdropPhysics.Hit? = null
        var time = 0f
        while (hit == null && time < HALF_MINUTE) {
            engine.step(FRAME) { hit = it }
            time += FRAME
        }
        return Jump(time, checkNotNull(hit) { "no hit within ${HALF_MINUTE}s" })
    }

    private companion object {
        const val FRAME = 1f / 60f
        const val HALF_MINUTE = 30f
        const val TURN_SECONDS = 20f
        const val TURN_TOLERANCE = 0.01f
        const val MIN_JUMPS = 8
        const val MIN_GAP = 2.5f
        const val MAX_GAP_SPREAD = 0.15f
        const val MAX_SPEED_RATIO = 1.1f
        const val SETTLE_MARGIN = 0.1f
        const val SETTLE_WINDOW = 0.5f
        const val HOME_TOLERANCE = 0.5f
        const val LINE_TOLERANCE = 2f
        const val REACH = 1.2f
        const val TOUCH_TOLERANCE = 5f
    }
}
