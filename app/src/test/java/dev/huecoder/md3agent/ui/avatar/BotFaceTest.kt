package dev.huecoder.md3agent.ui.avatar

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BotFaceTest {
    private class Frame(val at: Long, val glyph: EyeGlyph, val scale: Float)

    private class Stage {
        val face = BotFace()
        val frames = mutableListOf<Frame>()
        var wanted by mutableStateOf(EyeGlyph.Plain)

        fun want(glyph: EyeGlyph) {
            wanted = glyph
            Snapshot.sendApplyNotifications()
        }

        fun framesAfter(at: Long) = frames.filter { it.at >= at }
    }

    private fun TestScope.frameClock(tick: () -> Unit) = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(FRAME_MS)
            tick()
            return onFrame(currentTime * NANOS_PER_MILLI)
        }
    }

    private fun TestScope.follow(stage: Stage): Job {
        val clock = frameClock { stage.frames += Frame(currentTime, stage.face.glyph, stage.face.scale.value) }
        return launch(clock) { stage.face.follow { stage.wanted } }
    }

    @Test
    fun `a face starts plain at full size`() = runTest {
        val stage = Stage()
        val following = follow(stage)
        delay(200)
        assertEquals(EyeGlyph.Plain, stage.face.glyph)
        assertEquals(1f, stage.face.scale.value, 1e-4f)
        following.cancel()
    }

    @Test
    fun `the old eyes shrink to a dot before the new glyph pops out of it and overshoots`() = runTest {
        val stage = Stage()
        val following = follow(stage)
        delay(100)
        val requestedAt = currentTime
        stage.want(EyeGlyph.Squint)
        delay(700)
        val after = stage.framesAfter(requestedAt)
        val firstNew = after.first { it.glyph == EyeGlyph.Squint }
        assertTrue("old eyes still big at ${firstNew.scale}", firstNew.scale < 0.2f)
        assertTrue("glyph came ${firstNew.at - requestedAt}ms late", firstNew.at - requestedAt <= LATEST_SWAP_MS)
        val peak = after.filter { it.glyph == EyeGlyph.Squint }.maxOf { it.scale }
        assertTrue("overshoot $peak", peak in 1.08f..1.25f)
        assertEquals(EyeGlyph.Squint, stage.face.glyph)
        assertEquals(1f, stage.face.scale.value, 0.01f)
        following.cancel()
    }

    @Test
    fun `the face is never empty while one glyph hands over to another`() = runTest {
        val stage = Stage()
        val following = follow(stage)
        delay(100)
        val requestedAt = currentTime
        stage.want(EyeGlyph.Cross)
        delay(700)
        val smallest = stage.framesAfter(requestedAt).minOf { it.scale }
        assertTrue("face went down to $smallest", smallest >= 0.1f)
        following.cancel()
    }

    @Test
    fun `the glyph settles at full size quickly`() = runTest {
        val stage = Stage()
        val following = follow(stage)
        delay(100)
        val requestedAt = currentTime
        stage.want(EyeGlyph.Cross)
        delay(700)
        val settledAt = stage.framesAfter(requestedAt).last { abs(it.scale - 1f) > 0.03f }.at
        assertTrue("settled after ${settledAt - requestedAt}ms", settledAt - requestedAt <= LATEST_SETTLE_MS)
        following.cancel()
    }

    @Test
    fun `a squint can hand over to a cross and then back to plain eyes`() = runTest {
        val stage = Stage()
        val following = follow(stage)
        stage.want(EyeGlyph.Squint)
        delay(500)
        stage.want(EyeGlyph.Cross)
        delay(500)
        assertEquals(EyeGlyph.Cross, stage.face.glyph)
        stage.want(EyeGlyph.Plain)
        delay(500)
        assertEquals(EyeGlyph.Plain, stage.face.glyph)
        assertEquals(1f, stage.face.scale.value, 0.01f)
        following.cancel()
    }

    @Test
    fun `changing your mind mid pop never leaves the eyes invisible`() = runTest {
        val stage = Stage()
        val following = follow(stage)
        delay(100)
        listOf(EyeGlyph.Squint, EyeGlyph.Plain, EyeGlyph.Cross, EyeGlyph.Squint, EyeGlyph.Plain, EyeGlyph.Squint).forEach { glyph ->
            stage.want(glyph)
            delay(25)
        }
        delay(700)
        assertEquals(EyeGlyph.Squint, stage.face.glyph)
        assertEquals(1f, stage.face.scale.value, 0.01f)
        following.cancel()
    }

    @Test
    fun `asking for the glyph that is already shown does not make it blink`() = runTest {
        val stage = Stage()
        val following = follow(stage)
        delay(100)
        val requestedAt = currentTime
        stage.want(EyeGlyph.Plain)
        delay(300)
        assertTrue(stage.framesAfter(requestedAt).all { it.scale >= 0.99f })
        following.cancel()
    }

    private companion object {
        const val FRAME_MS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
        const val LATEST_SWAP_MS = 100L
        const val LATEST_SETTLE_MS = 400L
    }
}
