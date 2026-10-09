package dev.huecoder.md3agent.feature.playground

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaygroundTest {
    private val area = Size(1080f, 2000f)

    private fun playground(onImpact: (Float) -> Unit = {}) =
        Playground(pixelsPerMeter = 176f, palette = PlaygroundPalette(Color(0xFFB0C6FF)), pieceSizes = 60f..385f, onImpact = onImpact)
            .apply { resize(area) }

    private fun Playground.run(seconds: Float) = repeat((seconds / FRAME).toInt()) { step(FRAME) }

    @Test
    fun `shapes rain down and pile up on the floor without falling through`() {
        var impacts = 0
        val playground = playground { impacts++ }
        playground.run(8f)
        assertEquals(16, playground.pieces.size)
        playground.pieces.forEach { piece ->
            val center = playground.origin(piece)
            assertTrue("piece fell through at $center", center.y < area.height + piece.size)
            assertTrue("piece left the sides at $center", center.x in -piece.size..area.width + piece.size)
        }
        assertTrue("landings shook the phone", impacts > 0)
    }

    @Test
    fun `a piece thrown above the screen disappears and a new one falls in`() {
        val playground = playground()
        playground.run(6f)
        val thrown = playground.pieces.minBy { playground.origin(it).y }
        val start = playground.origin(thrown)
        assertTrue(playground.grab(POINTER, start))
        repeat(FLING_FRAMES) { frame ->
            playground.drag(POINTER, start - Offset(0f, (frame + 1) * FLING_STEP))
            playground.step(FRAME)
        }
        playground.release(POINTER)
        playground.run(1f)
        assertTrue("thrown piece is gone", playground.pieces.none { it === thrown })
        playground.run(2f)
        assertEquals(16, playground.pieces.size)
    }

    private companion object {
        const val FRAME = 1f / 60f
        const val POINTER = 1L
        const val FLING_FRAMES = 12
        const val FLING_STEP = 140f
    }
}
