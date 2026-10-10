package dev.huecoder.md3agent.ui.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

class EyeGlyphTest {
    private val safe = 0.4f
    private val glyphs = listOf(EyeGlyph.Squint, EyeGlyph.Cross)
    private val grid = (-5..5).map { it / 5f }

    private fun eyes(
        glyph: EyeGlyph,
        scale: Float = 1f,
        orientation: Orientation = Orientation.Identity,
        gaze: Gaze = Gaze.Center,
    ) = BotGeometry.eyes(orientation, blink = 0f, safeRadius = safe, gaze = gaze, glyph = glyph, scale = scale)

    private fun turnedAround(radians: Float) = Orientation.around(Vec3(0f, 1f, 0f), radians)

    private fun Eye.points(): List<Pair<Float, Float>> = outlines.flatMap { outline -> outline.xs.indices.map { outline.xs[it] to outline.ys[it] } }

    private fun signedArea(outline: Outline): Float {
        var twice = 0f
        outline.xs.indices.forEach { index ->
            val next = (index + 1) % outline.xs.size
            twice += outline.xs[index] * outline.ys[next] - outline.xs[next] * outline.ys[index]
        }
        return twice / 2f
    }

    @Test
    fun `glyphs stay inside the safe circle for every gaze and pop size`() {
        glyphs.forEach { glyph ->
            listOf(0.2f, 1f, 1.3f).forEach { scale ->
                grid.forEach { dx ->
                    grid.forEach { dy ->
                        val (left, right) = eyes(glyph, scale, gaze = BotGeometry.gazeTowards(dx * 2, dy * 2))
                        assertTrue("$glyph $scale left", BotGeometry.isInside(left!!, safe))
                        assertTrue("$glyph $scale right", BotGeometry.isInside(right!!, safe))
                    }
                }
            }
        }
    }

    @Test
    fun `the two eyes mirror each other`() {
        glyphs.forEach { glyph ->
            val (left, right) = eyes(glyph)
            assertEquals(-left!!.centerX, right!!.centerX, 1e-4f)
            assertEquals(left.centerY, right.centerY, 1e-4f)
            assertEquals(left.width, right.width, 1e-4f)
            assertEquals(left.height, right.height, 1e-4f)
        }
    }

    @Test
    fun `glyphs sit exactly where the plain eyes sit`() {
        val (plainLeft, plainRight) = eyes(EyeGlyph.Plain)
        glyphs.forEach { glyph ->
            val (left, right) = eyes(glyph)
            assertEquals(plainLeft!!.centerX, left!!.centerX, 0.01f)
            assertEquals(plainLeft.centerY, left.centerY, 0.01f)
            assertEquals(plainRight!!.centerX, right!!.centerX, 0.01f)
            assertEquals(plainRight.centerY, right.centerY, 0.01f)
        }
    }

    @Test
    fun `the squint chevrons point their tips at the nose`() {
        val (left, right) = eyes(EyeGlyph.Squint)
        fun Eye.meanX(inBand: (Float) -> Boolean) = points().filter { inBand(it.second) }.map { it.first }.average()
        listOf(left!! to 1, right!! to -1).forEach { (eye, towardsNose) ->
            val topEdge = eye.centerY - eye.height / 2f
            val arms = eye.meanX { it < topEdge + eye.height * 0.2f }
            val elbow = eye.meanX { abs(it - eye.centerY) < eye.height * 0.1f }
            assertTrue("elbow $elbow arms $arms", (elbow - arms) * towardsNose > 0.0)
        }
    }

    @Test
    fun `glyphs turn with the sphere like the plain eyes do`() {
        glyphs.forEach { glyph ->
            val (straightLeft, straightRight) = eyes(glyph)
            val seen = eyes(glyph, orientation = turnedAround(1.0f)).toList().filterNotNull()
            assertEquals(2, seen.size)
            assertTrue("$glyph squeezed", seen.minOf { it.width } < minOf(straightLeft!!.width, straightRight!!.width) * 0.8f)
            seen.forEach { assertTrue(BotGeometry.isInside(it, safe)) }
        }
    }

    @Test
    fun `glyphs hide behind the sphere when it turns its back`() {
        glyphs.forEach { glyph ->
            val (left, right) = eyes(glyph, orientation = turnedAround(PI.toFloat()))
            assertNull(left)
            assertNull(right)
        }
    }

    @Test
    fun `a half hidden glyph keeps its visible part inside the circle`() {
        glyphs.forEach { glyph ->
            val seen = eyes(glyph, orientation = turnedAround(1.45f)).toList().filterNotNull()
            assertTrue("$glyph", seen.isNotEmpty())
            seen.forEach { assertTrue(BotGeometry.isInside(it, safe)) }
        }
    }

    @Test
    fun `scale shrinks the glyph down to nothing`() {
        (glyphs + EyeGlyph.Plain).forEach { glyph ->
            val full = eyes(glyph).first!!
            val half = eyes(glyph, scale = 0.5f).first!!
            assertTrue("$glyph half ${half.height} full ${full.height}", half.height < full.height * 0.7f)
            assertNull(eyes(glyph, scale = 0f).first)
            assertNull(eyes(glyph, scale = 0f).second)
        }
    }

    @Test
    fun `a plain eye is one outline and a glyph is two crossing strokes`() {
        assertEquals(1, eyes(EyeGlyph.Plain).first!!.outlines.size)
        glyphs.forEach { assertEquals(2, eyes(it).first!!.outlines.size) }
    }

    @Test
    fun `strokes of one eye wind the same way so their fill never cancels out`() {
        val turns = listOf(Orientation.Identity, turnedAround(0.5f), turnedAround(-0.9f), turnedAround(1.2f))
        glyphs.forEach { glyph ->
            turns.forEach { turn ->
                eyes(glyph, orientation = turn).toList().filterNotNull().forEach { eye ->
                    val directions = eye.outlines.map { sign(signedArea(it)) }
                    assertTrue("$glyph $directions", directions.all { it != 0f && it == directions.first() })
                }
            }
        }
    }
}
