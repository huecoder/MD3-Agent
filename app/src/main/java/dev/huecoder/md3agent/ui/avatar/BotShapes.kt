package dev.huecoder.md3agent.ui.avatar

import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.AgentShape
import java.util.concurrent.ConcurrentHashMap

class BotShapes private constructor(restShape: AgentShape, workingShapes: List<AgentShape>) {
    val working: List<RoundedPolygon> = workingShapes.map { it.polygon }

    val rest: RoundedPolygon = restShape.polygon

    val restSymmetryDegrees: Float = restShape.symmetryDegrees

    private val steps: List<Morph> = working.indices.map { Morph(working[it], working[(it + 1) % working.size]) }
    private val settles: List<Morph> = working.map { Morph(it, rest) }
    private val wakes: Morph = Morph(rest, working.first())
    private val still: Morph = Morph(rest, rest)

    val safeRadius: Float = (steps + settles + wakes).minOf(::narrowest) * SAFETY

    fun step(index: Int): Morph = steps[index % steps.size]

    fun settle(fromIndex: Int): Morph = settles[fromIndex % settles.size]

    fun wake(): Morph = wakes

    fun still(): Morph = still

    private fun narrowest(morph: Morph): Float =
        (0..PROGRESS_SAMPLES).minOf { BotGeometry.innerRadius(morph.asCubics(it / PROGRESS_SAMPLES.toFloat())) }

    private data class Key(val rest: AgentShape, val working: List<AgentShape>)

    companion object {
        private val cache = ConcurrentHashMap<Key, BotShapes>()

        fun of(restShape: AgentShape, workingShapes: List<AgentShape>): BotShapes {
            val working = workingShapes.distinct().takeIf { it.size >= AgentProfile.MIN_WORKING_SHAPES } ?: AgentProfile.DEFAULT_WORKING_SHAPES
            return cache.getOrPut(Key(restShape, working)) { BotShapes(restShape, working) }
        }

        private const val PROGRESS_SAMPLES = 20
        private const val SAFETY = 0.94f
    }
}
