package dev.huecoder.md3agent.ui.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.sqrt

class BotPunchTest {
    @Test
    fun `power ramps up with the combo and caps at full`() {
        assertEquals(0.35f, BotPunch.powerFor(1), 1e-4f)
        assertTrue(BotPunch.powerFor(10) > BotPunch.powerFor(5))
        assertEquals(1f, BotPunch.powerFor(BotPunch.KNOCKOUT_COMBO), 1e-4f)
    }

    @Test
    fun `impulse swings a resting spring out to the requested peak`() {
        val stiffness = 420f
        val damping = 0.42f
        val peak = 0.5f
        var position = 0.0
        var velocity = BotPunch.impulse(peak, stiffness, damping).toDouble()
        val omega = sqrt(stiffness.toDouble())
        var reached = 0.0
        val step = 1e-5
        repeat(50_000) {
            val acceleration = -stiffness * position - 2 * damping * omega * velocity
            velocity += acceleration * step
            position += velocity * step
            reached = max(reached, position)
        }
        assertEquals(peak.toDouble(), reached, 0.01)
    }
}
