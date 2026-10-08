package dev.huecoder.md3agent.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartSpecTest {
    private fun parse(raw: String) = ChartSpec.parse(Json.parseToJsonElement(raw).jsonObject)

    @Test
    fun `bar chart with two series is parsed`() {
        val spec = parse("""{"type":"bar","title":"Sales","labels":["Q1","Q2"],"series":[{"name":"A","values":[1,2.5]},{"name":"B","values":[3,4]}]}""").getOrThrow()

        assertEquals(ChartKind.BAR, spec.kind)
        assertEquals(listOf(1.0, 2.5), spec.series.first().values)
    }

    @Test
    fun `series length must match labels`() {
        val error = parse("""{"type":"line","labels":["a","b","c"],"series":[{"values":[1,2]}]}""").exceptionOrNull()

        assertTrue(error is ToolArgumentException)
    }

    @Test
    fun `pie takes a single non negative series`() {
        assertTrue(parse("""{"type":"pie","labels":["a","b"],"series":[{"values":[1,-2]}]}""").isFailure)
        assertTrue(parse("""{"type":"pie","labels":["a","b"],"series":[{"values":[1,2]}]}""").isSuccess)
    }

    @Test
    fun `unknown chart type is rejected`() {
        assertTrue(parse("""{"type":"radar","labels":["a"],"series":[{"values":[1]}]}""").isFailure)
    }
}
