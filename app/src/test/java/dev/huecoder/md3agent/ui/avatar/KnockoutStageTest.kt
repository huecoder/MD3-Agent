package dev.huecoder.md3agent.ui.avatar

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnockoutStageTest {
    private val radius = 132f
    private val home = Offset(540f, 800f)
    private val pixelsPerMeter = 176f

    @Test
    fun `bot rolls over the ui off the screen, drops back in and ends upright at home`() = runTest {
        val stage = KnockoutStage().apply {
            setArea(Rect(0f, 0f, 1080f, 2000f))
            setObstacle("greeting", Rect(190f, 990f, 890f, 1100f))
            setObstacle("composer", Rect(33f, 1800f, 1047f, 1955f))
        }
        var impacts = 0
        val visible = Rect(0f, 0f, 1080f, 2000f).inflate(radius)
        var leftScreen = false
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(FRAME_MS)
                if (!visible.contains(home + stage.offset)) leftScreen = true
                return onFrame(currentTime * NANOS_PER_MILLI)
            }
        }
        withContext(clock) {
            stage.play(home, radius, away = Offset(1f, 0f), pixelsPerMeter) { impacts++ }
        }
        assertTrue("rolled off the screen", leftScreen)
        assertTrue("bounced off the UI on the way, got $impacts", impacts >= 2)
        assertTrue("finished in reasonable time, took ${currentTime}ms", currentTime < 15_000)
        assertEquals(Offset.Zero, stage.offset)
        assertEquals(0f, stage.spin)
        assertFalse(stage.isPlaying)
    }

    private companion object {
        const val FRAME_MS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
