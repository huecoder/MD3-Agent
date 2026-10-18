package dev.huecoder.md3agent.feature.onboarding.scene

import androidx.compose.ui.geometry.Offset
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.ui.avatar.polygon
import dev.huecoder.md3agent.ui.physics.ShapeParts
import dev.huecoder.md3agent.ui.physics.distanceToOutline
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BackdropPhysicsTest {
    private val stage = SceneStage(1080f, 2400f)
    private val fall = (stage.bottomCenter - stage.home).let { it / it.getDistance() }

    private class Jump(val time: Float, val hit: BackdropPhysics.Hit)

    private fun simulate(seconds: Float, seed: Int = 1, onFrame: (BackdropPhysics) -> Unit = {}): List<Jump> {
        val physics = BackdropPhysics(stage, ScenePlan.topShape, ScenePlan.bottomShape, Random(seed))
        val jumps = ArrayList<Jump>()
        var time = 0f
        while (time < seconds) {
            physics.step(FRAME) { jumps += Jump(time, it) }
            onFrame(physics)
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

    @Test
    fun `the top shape keeps jumping for minutes without stopping`() {
        (1..3).forEach { seed ->
            val strong = simulate(MINUTE, seed).filter { it.hit.metersPerSecond > STRONG_HIT }
            (0 until MINUTE.toInt() step WINDOW).forEach { start ->
                val inWindow = strong.count { it.time >= start && it.time < start + WINDOW }
                assertTrue("seed $seed: only $inWindow hits between ${start}s and ${start + WINDOW}s", inWindow >= MIN_HITS_PER_WINDOW)
            }
        }
    }

    @Test
    fun `the top shape stays on its line and near the home`() {
        simulate(MINUTE) { physics ->
            val offset = physics.topCenter - stage.home
            val along = offset.x * fall.x + offset.y * fall.y
            val lateral = (offset - fall * along).getDistance()
            assertTrue("drifted ${lateral}px off the line", lateral < LINE_TOLERANCE)
            assertTrue("went ${along}px along the line", abs(along) < stage.base * REACH)
        }
    }

    @Test
    fun `every first contact lies on the contours of both shapes`() {
        (1..5).forEach { seed ->
            val hit = checkNotNull(simulate(FIRST_LANDING, seed).firstOrNull()) { "seed $seed never landed" }.hit
            val bottom = contour(ScenePlan.bottomShape, stage.bottomSize, stage.bottomCenter, 0f)
            val top = contour(ScenePlan.topShape, stage.topSize, hit.topCenter, hit.topAngle)
            assertTrue("seed $seed: ${distanceToOutline(hit.point, bottom)}px from the bottom", distanceToOutline(hit.point, bottom) < TOUCH_TOLERANCE)
            assertTrue("seed $seed: ${distanceToOutline(hit.point, top)}px from the top", distanceToOutline(hit.point, top) < TOUCH_TOLERANCE)
        }
    }

    private companion object {
        const val FRAME = 1f / 60f
        const val MINUTE = 60f
        const val WINDOW = 10
        const val MIN_HITS_PER_WINDOW = 3
        const val STRONG_HIT = 2f
        const val FIRST_LANDING = 3f
        const val LINE_TOLERANCE = 2f
        const val REACH = 1.2f
        const val TOUCH_TOLERANCE = 3f
    }
}
