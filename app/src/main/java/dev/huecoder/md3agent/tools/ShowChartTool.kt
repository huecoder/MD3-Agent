package dev.huecoder.md3agent.tools

import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class ChartKind(val wireName: String) { LINE("line"), BAR("bar"), PIE("pie") }

data class ChartSeries(val name: String, val values: List<Double>)

data class ChartSpec(val kind: ChartKind, val title: String?, val unit: String?, val labels: List<String>, val series: List<ChartSeries>) {
    companion object {
        private const val MAX_POINTS = 60
        private const val MAX_SERIES = 6
        private const val MAX_LABEL_CHARS = 40

        fun parse(arguments: JsonObject): Result<ChartSpec> = runCatching {
            val kind = ChartKind.entries.firstOrNull { it.wireName == arguments.optionalString("type") }
                ?: throw ToolArgumentException("'type' must be one of ${ChartKind.entries.joinToString { it.wireName }}")
            val labels = arguments.optionalArray("labels").mapNotNull { (it as? JsonPrimitive)?.content?.take(MAX_LABEL_CHARS) }
            if (labels.isEmpty()) throw ToolArgumentException("Give at least one label in 'labels'")
            if (labels.size > MAX_POINTS) throw ToolArgumentException("At most $MAX_POINTS labels")
            val series = arguments.optionalArray("series").mapNotNull { (it as? JsonObject)?.let { raw -> parseSeries(raw, labels.size) } }
            if (series.isEmpty()) throw ToolArgumentException("Give at least one series with ${labels.size} numeric values")
            if (series.size > MAX_SERIES) throw ToolArgumentException("At most $MAX_SERIES series")
            if (kind == ChartKind.PIE && (series.size != 1 || series.single().values.any { it < 0 })) {
                throw ToolArgumentException("A pie chart takes exactly one series of non-negative values")
            }
            ChartSpec(kind, arguments.optionalString("title"), arguments.optionalString("unit"), labels, series)
        }

        private fun parseSeries(raw: JsonObject, size: Int): ChartSeries {
            val name = raw.optionalString("name").orEmpty()
            val values = (raw["values"] as? JsonArray).orEmpty().map {
                (it as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite) ?: throw ToolArgumentException("Series '$name' has a value that is not a number")
            }
            if (values.size != size) throw ToolArgumentException("Series '$name' has ${values.size} values but there are $size labels")
            return ChartSeries(name, values)
        }
    }
}

class ShowChartTool : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Draws a chart for the user right in the chat. Use it when numbers are easier to read as a picture: " +
            "trends over time as a line, comparisons as bars, shares of a whole as a pie. Mention the key takeaway in your text too.",
        parameters = objectSchema(required = listOf("type", "labels", "series")) {
            putJsonObject("type") {
                put("type", "string")
                putJsonArray("enum") { ChartKind.entries.forEach { add(it.wireName) } }
            }
            stringProperty("title", "Short chart title")
            stringProperty("unit", "Unit of the values, like °C, ₽ or %")
            stringArrayProperty("labels", "X axis labels, or slice names for a pie")
            putJsonObject("series") {
                put("type", "array")
                put("description", "Data series, each with one value per label. A pie takes exactly one series")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        stringProperty("name", "Series name for the legend")
                        putJsonObject("values") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "number") }
                        }
                    }
                    putJsonArray("required") { add("values") }
                }
            }
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        ChartSpec.parse(arguments).getOrThrow()
        return ToolOutcome("The chart is shown to the user")
    }

    companion object {
        const val NAME = "show_chart"
    }
}
