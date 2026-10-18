package dev.huecoder.md3agent.tools.web

import dev.huecoder.md3agent.data.settings.CustomSearch
import dev.huecoder.md3agent.data.settings.HeaderEntry
import dev.huecoder.md3agent.data.settings.NetworkSettings
import dev.huecoder.md3agent.data.settings.SearchBackend
import dev.huecoder.md3agent.data.settings.SearchMethod
import dev.huecoder.md3agent.data.settings.SearchPresets
import dev.huecoder.md3agent.data.settings.SearchSettings
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.tools.ToolArgumentException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomSearchApiTest {
    private val server = MockWebServer()
    private val exa = SearchPresets.EXA.template.copy(apiKey = "secret")

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `a post request fills the query and count into the body and the key into the header`() {
        val request = CustomSearchApi.request(exa, "say \"hi\"\nnow", 7)

        assertEquals("POST", request.method)
        assertEquals("https://api.exa.ai/search", request.url.toString())
        assertEquals("secret", request.header("x-api-key"))
        val body = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
        assertEquals("say \"hi\"\nnow", body.getValue("query").jsonPrimitive.content)
        assertEquals(7, body.getValue("numResults").jsonPrimitive.content.toInt())
    }

    @Test
    fun `a get request encodes the query in the address`() {
        val custom = CustomSearch(url = "https://search.example.com/v1?q={query}&n={count}&token={key}", method = SearchMethod.GET, apiKey = "a b")

        val request = CustomSearchApi.request(custom, "c++ & kotlin", 3)

        assertEquals("GET", request.method)
        assertEquals("https://search.example.com/v1?q=c%2B%2B+%26+kotlin&n=3&token=a+b", request.url.toString())
    }

    @Test(expected = ToolArgumentException::class)
    fun `a key placeholder without a key is refused`() {
        CustomSearchApi.request(exa.copy(apiKey = " "), "query", 5)
    }

    @Test(expected = ToolArgumentException::class)
    fun `a post without a body is refused`() {
        CustomSearchApi.request(CustomSearch(url = "https://search.example.com", method = SearchMethod.POST), "query", 5)
    }

    @Test
    fun `highlights are joined into the snippet and results without a link are skipped`() {
        val response = Json.parseToJsonElement(
            """{"results": [
                {"title": "One", "url": "https://one.example", "highlights": ["first part", "second part"]},
                {"title": "No link"},
                {"title": "Two", "url": "https://two.example", "text": "<b>Full</b> text"}
            ]}""",
        )

        val hits = CustomSearchApi.hits(response, exa)

        assertEquals(
            listOf(SearchHit("One", "https://one.example", "first part … second part"), SearchHit("Two", "https://two.example", "Full text")),
            hits,
        )
    }

    @Test
    fun `a nested results path and custom field names are followed`() {
        val custom = CustomSearch(url = "https://x.example", resultsPath = "data.web", titleField = "name", linkField = "meta.link", snippetFields = "summary")
        val response = Json.parseToJsonElement("""{"data": {"web": [{"name": "Page", "meta": {"link": "https://p.example"}, "summary": "About"}]}}""")

        assertEquals(listOf(SearchHit("Page", "https://p.example", "About")), CustomSearchApi.hits(response, custom))
    }

    @Test
    fun `a missing list names the keys the response has`() {
        val failure = runCatching { CustomSearchApi.hits(Json.parseToJsonElement("""{"error": "bad key", "code": 3}"""), exa) }.exceptionOrNull()

        assertTrue(failure is ToolArgumentException)
        assertTrue(failure?.message.orEmpty().contains("error, code"))
    }

    @Test
    fun `the searcher sends the custom request and reads the answer`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"results": [{"title": "Hit", "url": "https://hit.example", "highlights": ["found it"]}]}""").build())
        val searcher = WebSearcher(HttpClientProvider(flowOf(NetworkSettings()), TestScope()))
        val settings = SearchSettings(backend = SearchBackend.CUSTOM, custom = exa.copy(url = server.url("/search").toString()))

        val hits = searcher.search("exa query", 3, settings)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("secret", recorded.headers["x-api-key"])
        assertTrue(recorded.body?.utf8().orEmpty().contains("exa query"))
        assertEquals(listOf(SearchHit("Hit", "https://hit.example", "found it")), hits)
    }

    @Test
    fun `an extra header is sent with the key filled in and blank header names are dropped`() {
        val custom = CustomSearch(
            url = "https://x.example",
            method = SearchMethod.GET,
            apiKey = "k",
            headers = listOf(HeaderEntry("Authorization", "Bearer {key}"), HeaderEntry(" ", "ignored")),
        )

        val request = CustomSearchApi.request(custom, "q", 1)

        assertEquals("Bearer k", request.header("Authorization"))
    }
}
