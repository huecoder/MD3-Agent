package dev.huecoder.md3agent.feature.chat.diagram

import dev.huecoder.md3agent.tools.DiagramSpec
import kotlin.math.max

class LayeredGraph private constructor(
    val layers: List<List<String>>,
    val links: List<Pair<String, String>>,
    val chains: Map<Int, List<String>>,
) {
    companion object {
        fun build(spec: DiagramSpec): LayeredGraph {
            val ids = spec.nodes.map { it.id }
            val back = backEdges(ids, spec)
            val forward = spec.edges.withIndex().filter { it.index !in back }
            val layer = assignLayers(ids, forward.map { it.value.from to it.value.to })

            val layers = MutableList((layer.values.maxOrNull() ?: 0) + 1) { mutableListOf<String>() }
            ids.forEach { layers[layer.getValue(it)] += it }
            val links = mutableListOf<Pair<String, String>>()
            val chains = mutableMapOf<Int, List<String>>()
            forward.forEach { (index, edge) ->
                val dummies = (layer.getValue(edge.from) + 1 until layer.getValue(edge.to)).map { depth ->
                    "$DUMMY_PREFIX$index-$depth".also { layers[depth] += it }
                }
                val path = listOf(edge.from) + dummies + edge.to
                links += path.zipWithNext()
                chains[index] = dummies
            }
            order(layers, links)
            return LayeredGraph(layers, links, chains)
        }

        private fun backEdges(ids: List<String>, spec: DiagramSpec): Set<Int> {
            val outgoing = spec.edges.withIndex().groupBy({ it.value.from }, { it.index })
            val visiting = mutableSetOf<String>()
            val done = mutableSetOf<String>()
            val back = mutableSetOf<Int>()
            fun visit(id: String) {
                visiting += id
                outgoing[id].orEmpty().forEach { index ->
                    val target = spec.edges[index].to
                    if (target in visiting) back += index else if (target !in done) visit(target)
                }
                visiting -= id
                done += id
            }
            ids.forEach { if (it !in done) visit(it) }
            return back
        }

        private fun assignLayers(ids: List<String>, edges: List<Pair<String, String>>): Map<String, Int> {
            val incoming = ids.associateWith { id -> edges.count { it.second == id } }.toMutableMap()
            val layer = ids.associateWith { 0 }.toMutableMap()
            val ready = ArrayDeque(ids.filter { incoming[it] == 0 })
            while (ready.isNotEmpty()) {
                val id = ready.removeFirst()
                edges.filter { it.first == id }.forEach { (_, target) ->
                    layer[target] = max(layer.getValue(target), layer.getValue(id) + 1)
                    incoming[target] = incoming.getValue(target) - 1
                    if (incoming[target] == 0) ready += target
                }
            }
            ids.filter { id -> edges.none { it.second == id } }.forEach { source ->
                val nearest = edges.filter { it.first == source }.minOfOrNull { layer.getValue(it.second) } ?: return@forEach
                layer[source] = max(layer.getValue(source), nearest - 1)
            }
            return layer
        }

        private fun order(layers: List<MutableList<String>>, links: List<Pair<String, String>>) {
            repeat(ORDERING_SWEEPS) { sweep ->
                val downward = sweep % 2 == 0
                val range = if (downward) 1 until layers.size else (layers.size - 2) downTo 0
                for (index in range) {
                    val neighbor = layers[if (downward) index - 1 else index + 1].withIndex().associate { it.value to it.index.toFloat() }
                    val current = layers[index]
                    val weight = current.withIndex().associate { (slot, id) ->
                        val linked = links.mapNotNull { (from, to) -> if (downward && to == id) neighbor[from] else if (!downward && from == id) neighbor[to] else null }
                        id to (if (linked.isEmpty()) slot.toFloat() else linked.average().toFloat())
                    }
                    current.sortBy { weight.getValue(it) }
                }
            }
        }

        private const val DUMMY_PREFIX = "\u0000"
        private const val ORDERING_SWEEPS = 6
    }
}

object CrossPlacement {
    fun place(graph: LayeredGraph, span: (String) -> Float, gap: Float): Map<String, Float> {
        val center = mutableMapOf<String, Float>()
        graph.layers.forEach { layer -> pack(layer, layer.associateWith { 0f }, span, gap, center) }
        repeat(PASSES) { pass ->
            val downward = pass % 2 == 0
            val order = if (downward) graph.layers.indices.drop(1) else graph.layers.indices.reversed().drop(1)
            order.forEach { index ->
                val layer = graph.layers[index]
                val desired = layer.associateWith { id ->
                    val linked = graph.links.mapNotNull { (from, to) -> if (downward && to == id) center[from] else if (!downward && from == id) center[to] else null }
                    if (linked.isEmpty()) center.getValue(id) else linked.average().toFloat()
                }
                pack(layer, desired, span, gap, center)
            }
        }
        return center
    }

    private fun pack(layer: List<String>, desired: Map<String, Float>, span: (String) -> Float, gap: Float, out: MutableMap<String, Float>) {
        var edge = Float.NEGATIVE_INFINITY
        val placed = layer.map { id ->
            val half = span(id) / 2f
            val position = max(desired.getValue(id), edge + half)
            edge = position + half + gap
            position
        }
        val drift = layer.indices.map { desired.getValue(layer[it]) - placed[it] }.average().toFloat()
        layer.forEachIndexed { index, id -> out[id] = placed[index] + drift }
    }

    private const val PASSES = 4
}
