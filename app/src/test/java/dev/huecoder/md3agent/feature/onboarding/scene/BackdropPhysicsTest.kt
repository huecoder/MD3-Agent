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

    private class Strike(val hit: BackdropPhysics.Hit, val seconds: Float)

    private fun run(bottom: AgentShape, turn: Float): Strike {
        val physics = BackdropPhysics(stage, ScenePlan.topShape, bottom)
        physics.turnBottom(turn)
        physics.strike()
        var hit: BackdropPhysics.Hit? = null
        var seconds = 0f
        while (physics.step(FRAME) { if (hit == null) hit = it } && seconds < LIMIT) {
            seconds += FRAME
        }
        assertEquals("$bottom never settled", BackdropPhysics.Phase.REST, physics.phase)
        assertEquals(stage.home.x, physics.topCenter.x, 0.5f)
        assertEquals(stage.home.y, physics.topCenter.y, 0.5f)
        return Strike(checkNotNull(hit) { "$bottom was never hit" }, seconds)
    }

    private fun contour(shape: AgentShape, size: Float, center: Offset, turn: Float): List<Offset> =
        ShapeParts.outline(shape.polygon).map { corner ->
            val x = corner.x * size
            val y = corner.y * size
            Offset(x * cos(turn) - y * sin(turn), x * sin(turn) + y * cos(turn)) + center
        }

    @Test
    fun `the top shape lands on the contour of every bottom shape and comes home`() {
        ScenePlan.bottomShapes.forEachIndexed { step, shape ->
            val turn = ScenePlan.pose(step, calm = false).bottomTurn
            val strike = run(shape, turn)
            val bottomContour = contour(shape, stage.bottomSize, stage.bottomCenter, turn)
            val topContour = contour(ScenePlan.topShape, stage.topSize, strike.hit.topCenter, strike.hit.topAngle)
            val onBottom = distanceToOutline(strike.hit.point, bottomContour)
            val onTop = distanceToOutline(strike.hit.point, topContour)
            assertTrue("$shape: contact ${onBottom}px from the bottom contour", onBottom < TOUCH_TOLERANCE)
            assertTrue("$shape: contact ${onTop}px from the top contour", onTop < TOUCH_TOLERANCE)
            assertTrue("$shape: took ${strike.seconds}s", strike.seconds < SLOWEST_STRIKE)
        }
    }

    @Test
    fun `a strike that has already started is not restarted`() {
        val physics = BackdropPhysics(stage, ScenePlan.topShape, ScenePlan.bottomShapes.first())
        physics.strike()
        repeat(10) { physics.step(FRAME) {} }
        val before = physics.topCenter
        physics.strike()
        assertEquals(before, physics.topCenter)
    }

    @Test
    fun `reshaping the bottom keeps it in the same place`() {
        val physics = BackdropPhysics(stage, ScenePlan.topShape, AgentShape.COOKIE_6)
        physics.reshapeBottom(AgentShape.COOKIE_4)
        assertEquals(AgentShape.COOKIE_4, physics.bottomShape)
    }

    private companion object {
        const val FRAME = 1f / 60f
        const val LIMIT = 8f
        const val TOUCH_TOLERANCE = 3f
        const val SLOWEST_STRIKE = 5f
    }
}
