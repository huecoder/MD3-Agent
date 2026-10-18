package dev.huecoder.md3agent.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class AgentClusterTest {
    @Test
    fun `a cluster has five agents with different shapes and different colors`() {
        repeat(20) { seed ->
            val cluster = AgentCluster.random(Random(seed))
            assertEquals(AgentCluster.SIZE, cluster.size)
            assertEquals(AgentCluster.SIZE, cluster.map { it.shape }.toSet().size)
            assertEquals(AgentCluster.SIZE, cluster.map { it.tint }.toSet().size)
        }
    }

    @Test
    fun `every run brings a different set`() {
        assertEquals(false, AgentCluster.random(Random(1)) == AgentCluster.random(Random(2)))
    }
}
