package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PieSweepsTest {
    @Test
    fun `big slices keep their proportions when nothing is small`() {
        assertEquals(listOf(270f, 90f), pieSweeps(listOf(3.0, 1.0), minSweep = 10f))
    }

    @Test
    fun `tiny slices grow to the minimum and the big ones give up the difference`() {
        val sweeps = pieSweeps(listOf(65.0, 25.0, 8.0, 2.0, 1.0), minSweep = 15f)

        assertEquals(360f, sweeps.sum(), 0.01f)
        assertEquals(15f, sweeps[3], 0f)
        assertEquals(15f, sweeps[4], 0f)
        assertTrue(sweeps[0] > sweeps[1] && sweeps[1] > sweeps[2])
    }

    @Test
    fun `a slice squeezed under the minimum by the first pass is pinned too`() {
        assertEquals(listOf(160f, 100f, 100f), pieSweeps(listOf(10.0, 6.0, 1.0), minSweep = 100f))
    }

    @Test
    fun `empty slices stay empty`() {
        assertEquals(listOf(0f, 345f, 15f), pieSweeps(listOf(0.0, 99.0, 1.0), minSweep = 15f))
    }

    @Test
    fun `too many slices for the minimum share the circle equally`() {
        assertEquals(listOf(90f, 90f, 90f, 90f), pieSweeps(listOf(1.0, 1.0, 1.0, 1.0), minSweep = 100f))
    }
}
