package dev.huecoder.md3agent.tools.phone

import dev.huecoder.md3agent.system.control.ActionResult
import dev.huecoder.md3agent.system.control.ElementSelector
import dev.huecoder.md3agent.system.control.PhoneAction
import dev.huecoder.md3agent.system.control.PhoneReport
import dev.huecoder.md3agent.system.control.PhoneRequest
import dev.huecoder.md3agent.system.control.ScreenProbe
import dev.huecoder.md3agent.system.control.ScreenReading
import dev.huecoder.md3agent.system.control.ScreenSnapshot
import dev.huecoder.md3agent.system.control.ScriptPhone
import dev.huecoder.md3agent.system.control.StepReport
import dev.huecoder.md3agent.system.control.Target
import dev.huecoder.md3agent.system.control.testElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PhoneScriptBridgeTest {
    private class FakePhone : ScriptPhone {
        val begun = mutableListOf<String>()
        val captions = mutableListOf<String>()
        val requests = mutableListOf<PhoneRequest>()
        val reads = mutableListOf<Triple<String?, Boolean, ElementSelector?>>()
        var report: PhoneReport? = PhoneReport(listOf(StepReport("tap [1]", ActionResult.Done("Tapped [1]"))), true, null, null)
        var reading: ScreenReading? = null
        var gate: CompletableDeferred<Unit>? = null
        val running = AtomicInteger()
        val busiest = AtomicInteger()

        override fun begin(chatId: String) {
            begun += chatId
        }

        override fun say(caption: String) {
            captions += caption
        }

        override suspend fun run(request: PhoneRequest): PhoneReport? {
            val now = running.incrementAndGet()
            busiest.updateAndGet { maxOf(it, now) }
            try {
                requests += request
                gate?.await()
                return report
            } finally {
                running.decrementAndGet()
            }
        }

        override suspend fun read(say: String?, detailed: Boolean, find: ElementSelector?): ScreenReading? {
            reads += Triple(say, detailed, find)
            return reading
        }
    }

    private val phone = FakePhone()
    private val bridge = PhoneScriptBridge(phone, "chat-1")

    private fun call(operation: String, payload: String): JsonObject {
        val handle = bridge.begin(operation, payload)
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            bridge.poll(handle)?.let { return Json.parseToJsonElement(it).jsonObject }
            Thread.sleep(POLL_MS)
        }
        error("No reply for $operation within the time limit")
    }

    private fun snapshot(vararg elements: dev.huecoder.md3agent.system.control.ScreenElement) =
        ScreenSnapshot("com.android.settings", elements.toList(), 0, 1080, 2400, emptyList(), ScreenProbe("com.android.settings", 1L, emptyList()), emptyMap())

    @Test
    fun `an action is parsed, run and reported`() {
        val reply = call("act", """{"action":"tap","target":{"text":"Wi-Fi"},"observe":"none","say":"**Tapping**"}""")

        assertTrue(reply.getValue("ok").jsonPrimitive.boolean)
        assertTrue(reply.getValue("changed").jsonPrimitive.boolean)
        assertTrue(reply.getValue("text").jsonPrimitive.content.contains("Tapped [1]"))
        val request = phone.requests.single()
        assertEquals(PhoneAction.Tap(Target.Selected(ElementSelector(text = "Wi-Fi"))), request.steps.single().action)
        assertEquals("Tapping", request.steps.single().say)
        assertEquals(listOf("chat-1"), phone.begun)
    }

    @Test
    fun `a failed action is not ok but carries its text`() {
        phone.report = PhoneReport(listOf(StepReport("tap", ActionResult.Failed("No element matches"))), false, null, null)

        val reply = call("act", """{"action":"tap","element":3}""")

        assertFalse(reply.getValue("ok").jsonPrimitive.boolean)
        assertTrue(reply.getValue("text").jsonPrimitive.content.contains("No element matches"))
        assertFalse(reply.containsKey("error"))
    }

    @Test
    fun `an observed screen travels along`() {
        phone.report = PhoneReport(listOf(StepReport("press", ActionResult.Done("Pressed back"))), null, "App: x", null)

        val reply = call("act", """{"action":"press","key":"back","observe":"screen"}""")

        assertEquals("App: x", reply.getValue("screen").jsonPrimitive.content)
        assertFalse(reply.containsKey("changed"))
    }

    @Test
    fun `a disconnected phone is an error`() {
        phone.report = null

        val reply = call("act", """{"action":"press","key":"back"}""")

        assertFalse(reply.getValue("ok").jsonPrimitive.boolean)
        assertTrue(reply.getValue("error").jsonPrimitive.content.contains("Phone control is off"))
    }

    @Test
    fun `bad requests come back as errors and never reach the phone`() {
        val invalid = call("act", """{"action":"tap"}""")
        val unreadable = call("act", "not json")
        val notAnObject = call("act", "[1]")
        val unknown = call("fly", "{}")

        listOf(invalid, unreadable, notAnObject, unknown).forEach { reply ->
            assertFalse(reply.getValue("ok").jsonPrimitive.boolean)
            assertTrue(reply.getValue("error").jsonPrimitive.content.isNotBlank())
        }
        assertTrue(phone.requests.isEmpty())
        assertTrue(unknown.getValue("error").jsonPrimitive.content.contains("fly"))
    }

    @Test
    fun `a screen is described with structured elements`() {
        phone.reading = ScreenReading(
            snapshot(
                testElement(1, "Wi-Fi", role = "Switch", viewId = "wifi", clickable = true),
                testElement(2, "Bluetooth", role = "Switch"),
            ),
            "App: com.android.settings",
        )

        val reply = call("screen", """{"detailed":true,"say":"Looking"}""")

        assertEquals("com.android.settings", reply.getValue("app").jsonPrimitive.content)
        assertEquals(1080, reply.getValue("width").jsonPrimitive.int)
        assertEquals("App: com.android.settings", reply.getValue("text").jsonPrimitive.content)
        val elements: JsonArray = reply.getValue("elements").jsonArray
        assertEquals(2, elements.size)
        val first = elements[0].jsonObject
        assertEquals("Wi-Fi", first.getValue("label").jsonPrimitive.content)
        assertEquals("wifi", first.getValue("id").jsonPrimitive.content)
        assertEquals(540, first.getValue("x").jsonPrimitive.int)
        assertEquals(JsonPrimitive(null as String?), elements[1].jsonObject.getValue("id"))
        assertEquals(Triple<String?, Boolean, ElementSelector?>("Looking", true, null), phone.reads.single())
    }

    @Test
    fun `finding narrows the elements to the matches`() {
        phone.reading = ScreenReading(snapshot(testElement(1, "Wi-Fi"), testElement(2, "Bluetooth")), "text")

        val reply = call("screen", """{"find":{"text":"blue"}}""")

        assertEquals(listOf("Bluetooth"), reply.getValue("elements").jsonArray.map { it.jsonObject.getValue("label").jsonPrimitive.content })
        assertEquals(ElementSelector(text = "blue"), phone.reads.single().third)
    }

    @Test
    fun `a blank find and an unreachable screen are errors`() {
        val blank = call("screen", """{"find":{"exact":true}}""")
        val unreachable = call("screen", "{}")

        assertTrue(blank.getValue("error").jsonPrimitive.content.startsWith("find"))
        assertTrue(unreachable.getValue("error").jsonPrimitive.content.contains("Phone control is off"))
    }

    @Test
    fun `a caption is cleaned before it is shown`() {
        val reply = call("say", """{"text":"## **Almost** there"}""")
        val empty = call("say", """{"text":"  "}""")

        assertTrue(reply.getValue("ok").jsonPrimitive.boolean)
        assertEquals(listOf("Almost there"), phone.captions)
        assertFalse(empty.getValue("ok").jsonPrimitive.boolean)
    }

    @Test
    fun `a reply is not there until the action is done and is handed out once`() {
        phone.gate = CompletableDeferred()
        val handle = bridge.begin("act", """{"action":"press","key":"back"}""")
        Thread.sleep(SETTLE_MS)

        assertNull(bridge.poll(handle))
        phone.gate!!.complete(Unit)
        val reply = awaitReply(handle)

        assertTrue(Json.parseToJsonElement(reply).jsonObject.getValue("ok").jsonPrimitive.boolean)
        assertNull(bridge.poll(handle))
    }

    @Test
    fun `stopping cancels the action in flight`() {
        phone.gate = CompletableDeferred()
        val handle = bridge.begin("act", """{"action":"press","key":"back"}""")
        Thread.sleep(SETTLE_MS)

        bridge.abort()
        val reply = Json.parseToJsonElement(awaitReply(handle)).jsonObject

        assertTrue(reply.getValue("cancelled").jsonPrimitive.boolean)
        assertFalse(reply.getValue("ok").jsonPrimitive.boolean)
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (phone.running.get() > 0 && System.nanoTime() < deadline) Thread.sleep(POLL_MS)
        assertEquals("the action kept running after the stop", 0, phone.running.get())
    }

    @Test
    fun `after a stop nothing new is started`() {
        bridge.abort()

        val handle = bridge.begin("act", """{"action":"press","key":"back"}""")
        val reply = Json.parseToJsonElement(awaitReply(handle)).jsonObject

        assertTrue(reply.getValue("cancelled").jsonPrimitive.boolean)
        assertTrue(phone.requests.isEmpty())
    }

    @Test
    fun `calls from several threads take turns`() {
        phone.report = PhoneReport(listOf(StepReport("press", ActionResult.Done("ok"))), null, null, null)
        val slow = object : ScriptPhone by phone {
            override suspend fun run(request: PhoneRequest): PhoneReport? {
                delay(SETTLE_MS)
                return phone.run(request)
            }
        }
        val serial = PhoneScriptBridge(slow, "chat-2")

        val handles = (1..4).map { serial.begin("act", """{"action":"press","key":"back"}""") }
        handles.forEach { handle ->
            val deadline = System.nanoTime() + TIMEOUT_NANOS
            while (serial.poll(handle) == null && System.nanoTime() < deadline) Thread.sleep(POLL_MS)
        }

        assertEquals(1, phone.busiest.get())
        assertEquals(4, phone.requests.size)
    }

    private fun awaitReply(handle: Int): String {
        val deadline = System.nanoTime() + TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            bridge.poll(handle)?.let { return it }
            Thread.sleep(POLL_MS)
        }
        error("No reply within the time limit")
    }

    private companion object {
        const val TIMEOUT_NANOS = 5_000_000_000L
        const val POLL_MS = 5L
        const val SETTLE_MS = 80L
    }
}
