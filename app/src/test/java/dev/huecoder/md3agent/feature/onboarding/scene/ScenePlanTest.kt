package dev.huecoder.md3agent.feature.onboarding.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePlanTest {
    private val poses = (0..3).map { ScenePlan.pose(it, calm = it % 2 == 1) }

    @Test
    fun `neighbouring steps differ in scenery and colors`() {
        poses.zipWithNext().forEach { (first, second) ->
            assertNotEquals(first.scenery.map { it.shape }, second.scenery.map { it.shape })
            assertNotEquals(first.topColor, second.topColor)
        }
    }

    @Test
    fun `a position between two steps blends them`() {
        val blend = ScenePlan.blend(poses, 1.25f)
        assertSame(poses[1], blend.from)
        assertSame(poses[2], blend.to)
        assertEquals(0.25f, blend.fraction, 1e-6f)
        assertEquals(poses[1].intensity + (poses[2].intensity - poses[1].intensity) * 0.25f, blend.intensity, 1e-6f)
    }

    @Test
    fun `positions outside the range stick to the ends`() {
        assertSame(poses.first(), ScenePlan.blend(poses, -3f).from)
        val last = ScenePlan.blend(poses, 10f)
        assertSame(poses.last(), last.from)
        assertSame(poses.last(), last.to)
        assertEquals(0f, last.fraction, 0f)
    }

    @Test
    fun `calm steps draw fainter outlines`() {
        assertTrue(ScenePlan.pose(0, calm = true).intensity < ScenePlan.pose(0, calm = false).intensity)
    }

    @Test
    fun `the bottom shape has several different rounded forms to morph through`() {
        assertTrue(ScenePlan.bottomShapes.size >= 3)
        assertEquals(ScenePlan.bottomShapes.size, ScenePlan.bottomShapes.toSet().size)
        assertFalse(ScenePlan.topShape in ScenePlan.bottomShapes)
    }
}
