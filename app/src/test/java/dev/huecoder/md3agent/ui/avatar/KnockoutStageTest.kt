package dev.huecoder.md3agent.ui.avatar

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnockoutStageTest {
    private val radius = 132f
    private val home = Offset(540f, 800f)
    private val pixelsPerMeter = 176f
    private val area = Rect(0f, 0f, 1080f, 2000f)

    @Test
    fun `bot rolls over the ui off the screen, drops back in and ends upright at home`() = runTest {
        val stage = knockoutStage()
        var impacts = 0
        val visible = area.inflate(radius)
        var leftScreen = false
        var eyesRolled = false
        val clock = frameClock {
            if (!visible.contains(home + stage.offset)) leftScreen = true
            if (stage.orientation != Orientation.Identity) eyesRolled = true
        }
        withContext(clock) {
            stage.play(home, radius, hit = Offset(-1f, 0f), power = 1f, pixelsPerMeter) { impacts++ }
        }
        assertTrue("rolled off the screen", leftScreen)
        assertTrue("eyes rolled in 3d", eyesRolled)
        assertTrue("bounced off the UI on the way, got $impacts", impacts >= 2)
        assertTrue("finished in reasonable time, took ${currentTime}ms", currentTime < 15_000)
        assertEquals(Offset.Zero, stage.offset)
        assertEquals(0f, stage.spin)
        assertEquals(Orientation.Identity, stage.orientation)
        assertFalse(stage.isPlaying)
    }

    @Test
    fun `hitting it midair sends it off sooner`() = runTest {
        val calm = timeOnScreen(midairHits = 0)
        val beaten = timeOnScreen(midairHits = 3)
        assertTrue("beaten $beaten calm $calm", beaten < calm)
    }

    @Test
    fun `a layout change in flight sends the bot home at once`() = runTest {
        val stage = knockoutStage()
        var interruptedAt = 0L
        withContext(frameClock {}) {
            val flight = launch { stage.play(home, radius, hit = Offset(-1f, 0f), power = 1f, pixelsPerMeter) {} }
            delay(INTERRUPT_AFTER_MS)
            interruptedAt = currentTime
            stage.setArea(Rect(0f, 0f, 1080f, 1300f))
            flight.join()
        }
        assertTrue("took ${currentTime - interruptedAt}ms to give up", currentTime - interruptedAt <= 2 * FRAME_MS)
        assertBackHome(stage)
    }

    @Test
    fun `leaving the app in flight sends the bot home at once`() = runTest {
        val stage = knockoutStage()
        var interruptedAt = 0L
        withContext(frameClock {}) {
            val flight = launch { stage.play(home, radius, hit = Offset(-1f, 0f), power = 1f, pixelsPerMeter) {} }
            delay(INTERRUPT_AFTER_MS)
            interruptedAt = currentTime
            stage.abort()
            flight.join()
        }
        assertTrue("took ${currentTime - interruptedAt}ms to give up", currentTime - interruptedAt <= 2 * FRAME_MS)
        assertBackHome(stage)
    }

    @Test
    fun `the same layout reported again does not interrupt the flight`() = runTest {
        val stage = knockoutStage()
        withContext(frameClock {}) {
            val flight = launch { stage.play(home, radius, hit = Offset(-1f, 0f), power = 1f, pixelsPerMeter) {} }
            delay(INTERRUPT_AFTER_MS)
            stage.setArea(area)
            flight.join()
        }
        assertTrue("flew the whole way, ${currentTime}ms", currentTime > MIN_FULL_FLIGHT_MS)
    }

    private fun assertBackHome(stage: KnockoutStage) {
        assertEquals(Offset.Zero, stage.offset)
        assertEquals(0f, stage.spin)
        assertEquals(Orientation.Identity, stage.orientation)
        assertFalse(stage.isPlaying)
        assertFalse(stage.isDazed)
    }

    private suspend fun TestScope.timeOnScreen(midairHits: Int): Long {
        val stage = knockoutStage()
        val startedAt = currentTime
        var leftAt = 0L
        val clock = frameClock { if (!stage.isDazed && leftAt == 0L && currentTime > startedAt) leftAt = currentTime }
        withContext(clock) {
            val flight = launch { stage.play(home, radius, hit = Offset(-1f, 0f), power = 1f, pixelsPerMeter) {} }
            repeat(midairHits) {
                delay(HIT_GAP_MS)
                stage.hit(Offset(-1f, 0f), power = 1f)
            }
            flight.join()
        }
        return leftAt - startedAt
    }

    private fun knockoutStage() = KnockoutStage().apply {
        setArea(area)
        setObstacle("greeting", Rect(190f, 990f, 890f, 1100f))
        setObstacle("composer", Rect(33f, 1800f, 1047f, 1955f))
    }

    private fun TestScope.frameClock(tick: () -> Unit) = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(FRAME_MS)
            tick()
            return onFrame(currentTime * NANOS_PER_MILLI)
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val HIT_GAP_MS = 120L
        const val INTERRUPT_AFTER_MS = 400L
        const val MIN_FULL_FLIGHT_MS = 2_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
