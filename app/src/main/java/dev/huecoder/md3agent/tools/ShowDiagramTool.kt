package dev.huecoder.md3agent.tools

import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class DiagramDirection(val wireName: String) { DOWN("down"), RIGHT("right") }

enum class DiagramNodeKind(val wireName: String) { STEP("step"), DECISION("decision"), START("start"), END("end"), NOTE("note") }

data class DiagramNode(val id: String, val label: String, val kind: DiagramNodeKind)

data class DiagramEdge(val from: String, val to: String, val label: String?)

data class DiagramSpec(val title: String?, val direction: DiagramDirection, val nodes: List<DiagramNode>, val edges: List<DiagramEdge>) {
    companion object {
        private const val MAX_NODES = 30
        private const val MAX_EDGES = 60
        private const val MAX_LABEL_CHARS = 80
        private const val MAX_EDGE_LABEL_CHARS = 30

        fun parse(arguments: JsonObject): Result<DiagramSpec> = runCatching {
            val direction = arguments.optionalString("direction")?.let { raw ->
                DiagramDirection.entries.firstOrNull { it.wireName == raw } ?: throw ToolArgumentException("'direction' must be down or right")
            } ?: DiagramDirection.DOWN
            val nodes = arguments.optionalArray("nodes").mapNotNull { (it as? JsonObject)?.let(::parseNode) }
            if (nodes.isEmpty()) throw ToolArgumentException("Give at least one node in 'nodes'")
            if (nodes.size > MAX_NODES) throw ToolArgumentException("At most $MAX_NODES nodes, split the plan into several diagrams")
            val ids = nodes.map { it.id }
            if (ids.toSet().size != ids.size) throw ToolArgumentException("Node ids must be unique")
            val edges = arguments.optionalArray("edges").mapNotNull { (it as? JsonObject)?.let(::parseEdge) }
            if (edges.size > MAX_EDGES) throw ToolArgumentException("At most $MAX_EDGES edges")
            edges.firstOrNull { it.from !in ids || it.to !in ids }?.let {
                throw ToolArgumentException("Edge ${it.from} -> ${it.to} points to a node that does not exist")
            }
            DiagramSpec(arguments.optionalString("title"), direction, nodes, edges.filter { it.from != it.to })
        }

        private fun parseNode(raw: JsonObject): DiagramNode {
            val id = raw.requireString("id")
            val kind = raw.optionalString("kind")?.let { value -> DiagramNodeKind.entries.firstOrNull { it.wireName == value } } ?: DiagramNodeKind.STEP
            return DiagramNode(id, (raw.optionalString("label") ?: id).take(MAX_LABEL_CHARS), kind)
        }

        private fun parseEdge(raw: JsonObject) = DiagramEdge(
            from = raw.requireString("from"),
            to = raw.requireString("to"),
            label = raw.optionalString("label")?.take(MAX_EDGE_LABEL_CHARS),
        )
    }
}

class ShowDiagramTool : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Draws a flow diagram for the user right in the chat: blocks joined by arrows. " +
            "Use it for plans, step by step processes, decision trees and how parts of a system connect. " +
            "Keep labels short, a few words each, and still sum up the plan in your text.",
        parameters = objectSchema(required = listOf("nodes", "edges")) {
            stringProperty("title", "Short diagram title")
            enumProperty("direction", "Flow direction, down by default, right for short wide chains", DiagramDirection.entries.map { it.wireName })
            putJsonObject("nodes") {
                put("type", "array")
                put("description", "Blocks of the diagram")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        stringProperty("id", "Unique id used by edges")
                        stringProperty("label", "Text inside the block")
                        enumProperty("kind", "Block style, step by default", DiagramNodeKind.entries.map { it.wireName })
                    }
                    putJsonArray("required") { add("id"); add("label") }
                }
            }
            putJsonObject("edges") {
                put("type", "array")
                put("description", "Arrows between blocks")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        stringProperty("from", "Source node id")
                        stringProperty("to", "Target node id")
                        stringProperty("label", "Optional short arrow label, like yes or no")
                    }
                    putJsonArray("required") { add("from"); add("to") }
                }
            }
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        DiagramSpec.parse(arguments).getOrThrow()
        return ToolOutcome("The diagram is shown to the user")
    }

    companion object {
        const val NAME = "show_diagram"
    }
}

private fun JsonObjectBuilder.enumProperty(name: String, description: String, values: List<String>) = putJsonObject(name) {
    put("type", "string")
    put("description", description)
    putJsonArray("enum") { values.forEach { add(it) } }
}
