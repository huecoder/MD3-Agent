package dev.huecoder.md3agent.tools.phone

import dev.huecoder.md3agent.system.control.ElementSelector
import dev.huecoder.md3agent.system.control.GlobalKey
import dev.huecoder.md3agent.system.control.Observe
import dev.huecoder.md3agent.system.control.PhoneAction
import dev.huecoder.md3agent.system.control.ScreenPoint
import dev.huecoder.md3agent.system.control.SwipeDirection
import dev.huecoder.md3agent.system.control.Target
import dev.huecoder.md3agent.system.control.WaitCondition
import dev.huecoder.md3agent.tools.ToolArgumentException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRequestParserTest {
    private fun parse(json: String) = PhoneRequestParser.parse(Json.parseToJsonElement(json) as JsonObject)

    private fun single(json: String): PhoneAction = parse(json).steps.single().action

    @Test
    fun `a tap by number keeps working`() {
        assertEquals(PhoneAction.Tap(Target.Numbered(5)), single("""{"action":"tap","element":5,"say":"x"}"""))
    }

    @Test
    fun `a tap by text needs no number`() {
        val action = single("""{"action":"tap","target":{"text":"Wi-Fi","exact":true,"index":2}}""")

        assertEquals(PhoneAction.Tap(Target.Selected(ElementSelector(text = "Wi-Fi", exact = true, index = 2))), action)
    }

    @Test
    fun `a tap without any target says what to pass`() {
        val error = assertThrows(ToolArgumentException::class.java) { parse("""{"action":"tap"}""") }

        assertTrue(error.message!!.contains("element"))
        assertTrue(error.message!!.contains("target"))
    }

    @Test
    fun `a blank target is rejected and indexes count from one`() {
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"tap","target":{}}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"tap","target":{"text":"a","index":0}}""") }
    }

    @Test
    fun `typing takes the text and an optional field`() {
        val action = single("""{"action":"type","text":"hello","submit":true,"target":{"role":"Input"}}""")

        assertEquals(PhoneAction.Type(Target.Selected(ElementSelector(role = "Input")), "hello", true), action)
        assertEquals(PhoneAction.Type(null, "hi", false), single("""{"action":"type","text":"hi"}"""))
    }

    @Test
    fun `say is cleaned and kept per step`() {
        val request = parse("""{"action":"press","key":"back","say":"**Going back**"}""")

        assertEquals("Going back", request.steps.single().say)
    }

    @Test
    fun `wait_for needs a timeout in range`() {
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"wait_for","condition":"idle"}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"wait_for","condition":"idle","timeout_ms":50}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"wait_for","condition":"idle","timeout_ms":31000}""") }
        assertEquals(
            PhoneAction.WaitFor(WaitCondition.TextVisible("Connected"), 8_000),
            single("""{"action":"wait_for","condition":"text_visible","text":"Connected","timeout_ms":8000}"""),
        )
    }

    @Test
    fun `every wait condition parses`() {
        assertEquals(WaitCondition.TextGone("Loading"), (single("""{"action":"wait_for","condition":"text_gone","text":"Loading","timeout_ms":1000}""") as PhoneAction.WaitFor).condition)
        assertEquals(WaitCondition.AppInFront("settings"), (single("""{"action":"wait_for","condition":"app","app":"settings","timeout_ms":1000}""") as PhoneAction.WaitFor).condition)
        assertEquals(WaitCondition.Idle(), (single("""{"action":"wait_for","condition":"idle","timeout_ms":1000}""") as PhoneAction.WaitFor).condition)
        assertEquals(WaitCondition.ScreenChanged, (single("""{"action":"wait_for","condition":"screen_changed","timeout_ms":1000}""") as PhoneAction.WaitFor).condition)
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"wait_for","condition":"soon","timeout_ms":1000}""") }
    }

    @Test
    fun `scroll_to takes a target, an optional direction and a capped swipe count`() {
        val action = single("""{"action":"scroll_to","target":{"text":"Wi-Fi"},"max_swipes":99}""") as PhoneAction.ScrollTo

        assertEquals(ElementSelector(text = "Wi-Fi"), action.selector)
        assertNull(action.direction)
        assertEquals(PhoneRequestParser.MAX_SWIPES, action.maxSwipes)
        assertEquals(PhoneRequestParser.DEFAULT_MAX_SWIPES, (single("""{"action":"scroll_to","target":{"id":"title"}}""") as PhoneAction.ScrollTo).maxSwipes)
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"scroll_to"}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"scroll_to","target":{"text":"a"},"direction":"up_left"}""") }
    }

    @Test
    fun `scroll accepts only straight directions`() {
        assertEquals(PhoneAction.Scroll(null, SwipeDirection.DOWN), single("""{"action":"scroll","direction":"down"}"""))
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"scroll","direction":"down_left"}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"scroll"}""") }
    }

    @Test
    fun `a swipe takes a direction with diagonals or two points`() {
        val byDirection = single("""{"action":"swipe","direction":"up_right","distance":0.5,"duration_ms":500}""") as PhoneAction.Swipe
        assertEquals(SwipeDirection.UP_RIGHT, byDirection.spec.direction)
        assertEquals(0.5f, byDirection.spec.distance)
        assertEquals(500L, byDirection.spec.durationMs)

        val byPoints = single("""{"action":"swipe","from":{"x":100,"y":900},"to":{"x":100,"y":300}}""") as PhoneAction.Swipe
        assertEquals(ScreenPoint(100, 900), byPoints.spec.from)
        assertEquals(ScreenPoint(100, 300), byPoints.spec.to)

        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"swipe"}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"swipe","from":{"x":1,"y":2}}""") }
    }

    @Test
    fun `swipe distance and duration are clamped`() {
        val action = single("""{"action":"swipe","direction":"up","distance":5,"duration_ms":1}""") as PhoneAction.Swipe

        assertEquals(PhoneRequestParser.MAX_DISTANCE, action.spec.distance)
        assertEquals(PhoneRequestParser.MIN_DURATION_MS.toLong(), action.spec.durationMs)
    }

    @Test
    fun `a drag needs a path of points`() {
        val action = single("""{"action":"drag","path":[{"x":10,"y":20},{"x":30,"y":40},{"x":50,"y":60}],"duration_ms":900}""") as PhoneAction.Drag

        assertEquals(listOf(ScreenPoint(10, 20), ScreenPoint(30, 40), ScreenPoint(50, 60)), action.path)
        assertEquals(900L, action.durationMs)
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"drag","path":[{"x":10,"y":20}]}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"drag"}""") }
    }

    @Test
    fun `a batch parses each step with its own say`() {
        val request = parse(
            """{"say":"Connecting to Wi-Fi","observe":"changes","steps":[
                {"action":"open_app","app":"Settings","say":"Opening Settings"},
                {"action":"tap","target":{"text":"Network"}},
                {"action":"scroll_to","target":{"text":"Wi-Fi"}},
                {"action":"wait_for","condition":"text_visible","text":"Connected","timeout_ms":5000}
            ]}""",
        )

        assertEquals(4, request.steps.size)
        assertEquals(Observe.CHANGES, request.observe)
        assertTrue(request.stopOnError)
        assertEquals("Opening Settings", request.steps[0].say)
        assertNull(request.steps[1].say)
        assertEquals(PhoneAction.OpenApp("Settings"), request.steps[0].action)
    }

    @Test
    fun `a batch can carry on after a failure`() {
        val request = parse("""{"on_error":"continue","steps":[{"action":"press","key":"back"}]}""")

        assertFalse(request.stopOnError)
        assertEquals(PhoneAction.Press(GlobalKey.BACK), request.steps.single().action)
    }

    @Test
    fun `batches are bounded and cannot nest`() {
        assertThrows(ToolArgumentException::class.java) { parse("""{"steps":[]}""") }
        val eleven = (1..11).joinToString(",") { """{"action":"press","key":"back"}""" }
        assertThrows(ToolArgumentException::class.java) { parse("""{"steps":[$eleven]}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"steps":[{"action":"press","key":"back","steps":[]}]}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"steps":["tap"]}""") }
    }

    @Test
    fun `without action or steps there is nothing to do`() {
        assertThrows(ToolArgumentException::class.java) { parse("""{"say":"hello"}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"dance"}""") }
        assertThrows(ToolArgumentException::class.java) { parse("""{"action":"tap","element":1,"observe":"everything"}""") }
    }

    @Test
    fun `waiting a fixed time is clamped`() {
        assertEquals(PhoneAction.Wait(10_000), single("""{"action":"wait","ms":999999}"""))
        assertEquals(PhoneAction.Wait(100), single("""{"action":"wait","ms":1}"""))
        assertEquals(PhoneAction.Wait(1_500), single("""{"action":"wait"}"""))
    }
}
