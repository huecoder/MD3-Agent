package com.mwagent.tools.web

import com.mwagent.data.settings.FetchBackend
import com.mwagent.data.settings.FetchSettings
import com.mwagent.data.settings.NetworkSettings
import com.mwagent.net.HttpClientProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PageFetcherTest {
    private val server = MockWebServer()
    private lateinit var fetcher: PageFetcher
    private val page = "https://example.com/post?id=1".toHttpUrl()

    @Before
    fun setUp() {
        server.start()
        fetcher = PageFetcher(HttpClientProvider(flowOf(NetworkSettings()), TestScope()))
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String) = server.enqueue(MockResponse.Builder().body(body).build())

    @Test
    fun `custom reader gets the page address in place of the placeholder with the key as bearer`() = runTest {
        respond("# Post\n\nHello")
        val settings = FetchSettings(FetchBackend.CUSTOM, customUrl = server.url("/read/").toString() + "{url}", customApiKey = " k1 ")

        val result = fetcher.fetch(page, settings)

        val request = server.takeRequest()
        assertEquals("/read/https://example.com/post?id=1", request.target)
        assertEquals("Bearer k1", request.headers["Authorization"])
        assertEquals(FetchedPage(null, "# Post\n\nHello"), result)
    }

    @Test
    fun `reader without placeholder gets the address appended and no auth when key is blank`() = runTest {
        respond("text")
        fetcher.fetch(page, FetchSettings(FetchBackend.CUSTOM, customUrl = server.url("/r/").toString()))

        val request = server.takeRequest()
        assertEquals("/r/https://example.com/post?id=1", request.target)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `html answers are converted to text with the title`() = runTest {
        respond("<!DOCTYPE html><html><head><title>Hi</title></head><body><p>One</p><p>Two</p></body></html>")
        val result = fetcher.fetch(page, FetchSettings(FetchBackend.CUSTOM, customUrl = server.url("/").toString()))

        assertEquals("Hi", result.title)
        assertEquals("One\nTwo", result.text)
    }
}
