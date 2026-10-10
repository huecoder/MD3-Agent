package dev.huecoder.md3agent.ui.avatar

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class BotPunchTest {
    private fun TestScope.frameClock() = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(FRAME_MS)
            return onFrame(currentTime * NANOS_PER_MILLI)
        }
    }

    @Test
    fun `power ramps up with the combo and caps at full`() {
        assertEquals(0.35f, BotPunch.powerFor(1, tempo = 0f), 1e-4f)
        assertTrue(BotPunch.powerFor(10, tempo = 0f) > BotPunch.powerFor(5, tempo = 0f))
        assertEquals(1f, BotPunch.powerFor(BotPunch.KNOCKOUT_COMBO, tempo = 1f), 1e-4f)
    }

    @Test
    fun `faster hits land harder`() {
        val slow = BotPunch.powerFor(4, BotPunch.tempoOf(400))
        val fast = BotPunch.powerFor(4, BotPunch.tempoOf(100))
        assertTrue("fast $fast slow $slow", fast > slow * 1.4f)
        assertEquals(0f, BotPunch.tempoOf(5_000), 0f)
        assertEquals(1f, BotPunch.tempoOf(60), 0f)
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

    @Test
    fun `a hit shoves the gaze away from the tap and lets it come back`() = runTest {
        val punch = BotPunch()
        var farthest = Offset.Zero
        withContext(frameClock()) {
            punch.hit(Offset(1f, 0f), currentTime, this)
            repeat(40) {
                delay(FRAME_MS)
                if (punch.gazeKick.value.getDistance() > farthest.getDistance()) farthest = punch.gazeKick.value
            }
            delay(2_000)
        }
        assertTrue("pushed to $farthest", farthest.x < -0.15f)
        assertTrue("sideways drift ${farthest.y}", abs(farthest.y) < 0.01f)
        assertTrue("never came back, at ${punch.gazeKick.value}", punch.gazeKick.value.getDistance() < 0.02f)
    }

    @Test
    fun `a harder hit shoves the gaze further`() = runTest {
        suspend fun TestScope.farthestAfter(hits: Int): Float {
            val punch = BotPunch()
            var farthest = 0f
            withContext(frameClock()) {
                repeat(hits) {
                    punch.hit(Offset(0f, 1f), currentTime, this)
                    delay(100)
                    farthest = max(farthest, punch.gazeKick.value.getDistance())
                }
            }
            return farthest
        }
        val first = farthestAfter(1)
        val barrage = farthestAfter(12)
        assertTrue("first $first barrage $barrage", barrage > first * 1.2f)
    }

    @Test
    fun `eyes stay squeezed through a barrage and relax after it`() = runTest {
        val punch = BotPunch()
        var squeezedThroughout = true
        withContext(frameClock()) {
            punch.hit(Offset(0.5f, 0.2f), currentTime, this)
            assertTrue(punch.isSqueezing)
            repeat(4) {
                delay(150)
                squeezedThroughout = squeezedThroughout && punch.isSqueezing
                punch.hit(Offset(-0.5f, 0.2f), currentTime, this)
            }
            delay(MAX_SQUEEZE_MS + FRAME_MS)
        }
        assertTrue(squeezedThroughout)
        assertFalse(punch.isSqueezing)
    }

    private companion object {
        const val FRAME_MS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
        const val MAX_SQUEEZE_MS = 430L
    }
}
