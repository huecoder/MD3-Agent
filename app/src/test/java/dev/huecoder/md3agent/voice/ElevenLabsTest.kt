package dev.huecoder.md3agent.voice

import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.NetworkSettings
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.VoiceProvider
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.SpeechVoice
import dev.huecoder.md3agent.llm.TranscriptionClient
import dev.huecoder.md3agent.llm.VoiceListClient
import dev.huecoder.md3agent.net.HttpClientProvider
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ElevenLabsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var httpClients: HttpClientProvider

    @Before
    fun setUp() {
        server.start()
        httpClients = HttpClientProvider(flowOf(NetworkSettings()), TestScope())
    }

    @After
    fun tearDown() = server.close()

    private val baseUrl get() = server.url("/v1").toString()

    private fun profile() = ProviderProfile("p", "ElevenLabs", baseUrl, apiKey = "key-1", format = ApiFormat.ELEVENLABS)

    private fun voiceProvider(voice: String = "voice-1") = VoiceProvider(
        id = "p",
        name = "ElevenLabs",
        baseUrl = baseUrl,
        apiKey = "key-1",
        speechModel = "eleven_multilingual_v2",
        speechVoice = voice,
        format = ApiFormat.ELEVENLABS,
    )

    @Test
    fun `speech goes to the voice path with the key header and the chosen model`() = runTest {
        val audio = byteArrayOf(1, 2, 3, 4)
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "audio/mpeg").body(Buffer().write(audio)).build())

        val speech = ProviderSpeechSynthesizer(httpClients, voiceProvider(), temp.newFolder("cache")).prepare("Привет, мир")

        val request = server.takeRequest()
        assertEquals("/v1/text-to-speech/voice-1", request.url.encodedPath)
        assertEquals("key-1", request.headers["xi-api-key"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("Привет, мир", body["text"]!!.jsonPrimitive.content)
        assertEquals("eleven_multilingual_v2", body["model_id"]!!.jsonPrimitive.content)
        assertArrayEquals(audio, (speech as PreparedSpeech.Audio).file.readBytes())
    }

    @Test
    fun `a voice id with odd characters stays one path segment`() = runTest {
        server.enqueue(MockResponse.Builder().body(Buffer().write(byteArrayOf(1))).build())

        ProviderSpeechSynthesizer(httpClients, voiceProvider("a/b c"), temp.newFolder("cache")).prepare("hi")

        assertEquals("/v1/text-to-speech/a%2Fb%20c", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `a json body in place of audio is reported with its text`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"detail":{"message":"quota exceeded"}}""").build())

        val failure = runCatching { ProviderSpeechSynthesizer(httpClients, voiceProvider(), temp.newFolder("cache")).prepare("hi") }.exceptionOrNull()

        assertTrue(failure is ProviderException.MalformedResponse)
        assertTrue(failure!!.message!!.contains("quota exceeded"))
    }

    @Test
    fun `transcription posts a scribe form and reads the text`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"language_code":"rus","language_probability":0.99,"text":"  привет мир  ","words":[]}""").build())
        val audio = temp.newFile("rec.ogg").apply { writeBytes(byteArrayOf(9, 9)) }

        val text = TranscriptionClient(httpClients).transcribe(profile(), "scribe_v2", "ru", audio, "audio/ogg")

        assertEquals("привет мир", text)
        val request = server.takeRequest()
        assertEquals("/v1/speech-to-text", request.url.encodedPath)
        assertEquals("key-1", request.headers["xi-api-key"])
        val form = request.body!!.utf8()
        listOf("model_id", "scribe_v2", "language_code", "ru", "tag_audio_events", "false", "name=\"file\"").forEach {
            assertTrue("form lacks $it", form.contains(it))
        }
    }

    @Test
    fun `voices are read from the account list and sorted by name`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"voices":[{"voice_id":"b","name":"Rachel","category":"premade"},{"voice_id":"a","name":"adam"},{"voice_id":"b","name":"Rachel"},{"name":"no id"}]}""",
            ).build(),
        )

        val voices = VoiceListClient(httpClients).list(profile())

        assertEquals(listOf(SpeechVoice("a", "adam"), SpeechVoice("b", "Rachel", traits = listOf("Premade"))), voices)
        val request = server.takeRequest()
        assertEquals("/v1/voices", request.url.encodedPath)
        assertEquals("key-1", request.headers["xi-api-key"])
    }

    @Test
    fun `a library of hundreds of heavy entries is read in full`() = runTest {
        val padding = "x".repeat(4_000)
        val body = (1..700).joinToString(prefix = """{"voices":[""", postfix = "]}", separator = ",") { index ->
            """{"voice_id":"v$index","name":"Voice $index","samples":null,"fine_tuning":{"state":{"a":"b"}},"description":"$padding"}"""
        }
        assertTrue("the body must be larger than the capped text reader allows", body.length > 2 * 1024 * 1024)
        server.enqueue(MockResponse.Builder().body(body).build())

        val voices = VoiceListClient(httpClients).list(profile())

        assertEquals(700, voices.size)
        assertTrue(voices.any { it.id == "v700" })
    }

    @Test
    fun `a body without a voices array is reported as an unknown format`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"detail":"nothing here"}""").build())

        try {
            VoiceListClient(httpClients).list(profile())
            fail("expected a failure")
        } catch (e: ProviderException.MalformedResponse) {
            assertTrue(e.message.orEmpty().contains("voice list"))
        }
    }

    @Test
    fun `a rejected key surfaces as an http error`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":{"status":"invalid_api_key"}}""").build())

        try {
            VoiceListClient(httpClients).list(profile())
            fail("expected a failure")
        } catch (e: ProviderException.Http) {
            assertEquals(401, e.code)
        }
    }

    @Test
    fun `a voice carries its labels description and preview link`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"voices":[{"voice_id":"v1","name":"Jon","category":"premade","description":"Warm and calm",
                    "labels":{"accent":"american","gender":"male","age":"middle_aged","use_case":"conversational","language":"en","mood":"calm"},
                    "preview_url":"https://cdn.example/jon.mp3"}]}""",
            ).build(),
        )

        val voice = VoiceListClient(httpClients).list(profile()).single()

        assertEquals("Warm and calm", voice.description)
        assertEquals("https://cdn.example/jon.mp3", voice.previewUrl)
        assertEquals(listOf("Premade", "Male", "Middle aged", "American", "Conversational", "EN", "Calm"), voice.traits)
    }

    @Test
    fun `gemini voices come from the prebuilt list with their traits and no preview`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"voices":[
                    {"id":"Puck","display_name":"Puck","language_code":"en-US","accent":"American","gender":"male","pitch":"medium","persona":"Upbeat","context":"Conversational","description":"Playful"},
                    {"id":"Kore","displayName":"Kore","gender":"female"}
                ]}""",
            ).build(),
        )

        val voices = VoiceListClient(httpClients).list(profile().copy(format = ApiFormat.GEMINI, baseUrl = server.url("/v1beta").toString()))

        assertEquals(listOf("Kore", "Puck"), voices.map { it.id })
        val puck = voices.last()
        assertEquals(listOf("Male", "Medium pitch", "American", "en-US", "Upbeat", "Conversational"), puck.traits)
        assertEquals("Playful", puck.description)
        assertEquals(null, puck.previewUrl)
        val request = server.takeRequest()
        assertEquals("/v1beta/voices", request.url.encodedPath)
        assertEquals("prebuilt", request.url.queryParameter("type"))
        assertEquals("key-1", request.headers["x-goog-api-key"])
    }

    @Test
    fun `gemini voices are read page by page`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"voices":[{"id":"A"}],"next_page_token":"t2"}""").build())
        server.enqueue(MockResponse.Builder().body("""{"voices":[{"id":"B"}]}""").build())

        val voices = VoiceListClient(httpClients).list(profile().copy(format = ApiFormat.GEMINI, baseUrl = server.url("/v1beta").toString()))

        assertEquals(listOf("A", "B"), voices.map { it.id })
        server.takeRequest()
        assertEquals("t2", server.takeRequest().url.queryParameter("page_token"))
    }

    @Test
    fun `openai voices are the built in names without a request`() = runTest {
        val voices = VoiceListClient(httpClients).list(profile().copy(format = ApiFormat.OPENAI))

        assertTrue(voices.any { it.id == "marin" && it.name == "Marin" })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `anthropic has no voice list`() = runTest {
        assertTrue(VoiceListClient(httpClients).list(profile().copy(format = ApiFormat.ANTHROPIC)).isEmpty())
    }
}
