package dev.huecoder.md3assistant.tools

import dev.huecoder.md3assistant.data.settings.ToolPermission
import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.llm.ToolCall
import dev.huecoder.md3assistant.llm.ToolSpec
import dev.huecoder.md3assistant.tools.web.DuckDuckGoHtml
import dev.huecoder.md3assistant.tools.web.HtmlText
import dev.huecoder.md3assistant.tools.web.SearchHit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsParsingTest {
    @Test
    fun `duckduckgo html results unwrap redirects and skip ads`() {
        val html = """
            <div class="result results_links result--ad"><div class="links_main links_deep result__body">
              <a rel="nofollow" class="result__a" href="https://duckduckgo.com/y.js?ad_domain=x">Реклама</a>
            </div></div>
            <div class="result results_links"><div class="links_main links_deep result__body">
              <h2 class="result__title"><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fopen-meteo.com%2Fen%2Fdocs&amp;rut=abc">Open&#x2011;Meteo <b>API</b></a></h2>
              <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">Free weather API &amp; forecasts</a>
            </div></div>
        """.trimIndent()

        assertEquals(
            listOf(SearchHit("Open‑Meteo API", "https://open-meteo.com/en/docs", "Free weather API & forecasts")),
            DuckDuckGoHtml.parse(html),
        )
    }

    @Test
    fun `page text drops scripts and navigation but keeps paragraphs and lists`() {
        val html = "<html><head><title>Док</title><script>var a=1</script></head><body><nav>Меню</nav>" +
            "<h1>Заголовок</h1><p>Первый&nbsp;абзац</p><ul><li>раз</li><li>два</li></ul></body></html>"

        assertEquals("Заголовок\nПервый абзац\n\n• раз\n• два", HtmlText.document(html))
        assertEquals("Док", HtmlText.title(html))
    }

    @Test
    fun `reply ui keeps valid entries and drops broken ones`() {
        val ui = ReplyUi.parse(
            """{"suggestions":["Да","Да"," ","Нет"],"buttons":[
                {"label":"Сайт","action":"open_url","value":"https://a.b"},
                {"label":"Опасно","action":"open_url","value":"javascript:alert(1)"},
                {"label":"Без действия","value":"x"},
                {"label":"Скопировать","action":"copy","value":"42"}]}""",
        )

        assertEquals(listOf("Да", "Нет"), ui.suggestions)
        assertEquals(listOf("Сайт", "Скопировать"), ui.buttons.map { it.label })
    }

    @Test
    fun `registry reports unknown, disabled and malformed calls as errors instead of throwing`() = runTest {
        val echo = object : Tool {
            override val spec = ToolSpec("echo", "", objectSchema(emptyList()) {})
            override suspend fun execute(arguments: JsonObject, settings: ToolSettings) = ToolOutcome(arguments.toString())
        }
        val registry = ToolRegistry(listOf(echo))

        assertTrue(registry.run(ToolCall("1", "missing", "{}"), ToolSettings(), ToolContext("chat")).isError)
        assertTrue(registry.run(ToolCall("2", "echo", "{}"), ToolSettings().withPermission("echo", ToolPermission.OFF), ToolContext("chat")).isError)
        assertTrue(registry.run(ToolCall("3", "echo", "not json"), ToolSettings(), ToolContext("chat")).isError)
        val ok = registry.run(ToolCall("4", "echo", """{"a":1}"""), ToolSettings(), ToolContext("chat"))
        assertFalse(ok.isError)
        assertEquals("""{"a":1}""", ok.content)
        assertEquals(emptyList<ToolSpec>(), registry.specs(ToolSettings().withPermission("echo", ToolPermission.OFF)))
    }
}
