package dev.huecoder.md3agent.tools.phone

import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.control.GlobalKey
import dev.huecoder.md3agent.system.control.Observe
import dev.huecoder.md3agent.system.control.PhoneControl
import dev.huecoder.md3agent.system.control.PhoneSay
import dev.huecoder.md3agent.system.control.ScreenRegion
import dev.huecoder.md3agent.system.control.ScreenshotResult
import dev.huecoder.md3agent.system.control.SwipeDirection
import dev.huecoder.md3agent.system.control.Waiter
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolContext
import dev.huecoder.md3agent.tools.ToolArgumentException
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.booleanProperty
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalBoolean
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.optionalString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

abstract class PhoneTool(protected val control: PhoneControl) : Tool {
    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        control.session.begin(context.chatId)
        return execute(arguments, settings)
    }
}

class PhoneScreenTool(control: PhoneControl) : PhoneTool(control) {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads what is on the phone screen right now as a numbered list of elements with their role, text and center coordinates. " +
            "Call it before ${PhoneActionTool.NAME} so element numbers are fresh.",
        parameters = objectSchema(required = emptyList()) {
            booleanProperty("detailed", "Also list each element's exact bounds, view class and resource id, like a trimmed UI hierarchy dump")
            sayProperty(required = false)
        },
    )

    override fun promptNote(settings: ToolSettings) = listOf(
        "You can operate the phone. Work in a loop: look with $NAME, act with ${PhoneActionTool.NAME} (it returns the new screen), read that screen, repeat until the goal is reached.",
        "Every phone call carries a 'say' phrase that the user sees next to the cursor: one short sentence, first person, in the user's language, about what you do right now, like \"Opening Settings\" or \"Scrolling to find Wi-Fi\". Plain text, no markdown, at most ${PhoneSay.MAX_CHARS} characters.",
        "If an action fails or changes nothing, do not repeat it more than twice: pick another element, scroll, or look at the pixels with ${PhoneScreenshotTool.NAME} (games, canvases and custom views have no text list).",
        "Never enter passwords or payment details, and never confirm purchases or deletions, unless the user said so in this chat.",
    ).joinToString("\n")

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val snapshot = control.screen(arguments.sayOrNull()) ?: return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        return ToolOutcome(snapshot.describe(detailed = arguments.optionalBoolean("detailed") ?: false))
    }

    companion object {
        const val NAME = "phone_screen"
    }
}

class PhoneScreenshotTool(control: PhoneControl, private val attachments: AttachmentStore) : PhoneTool(control) {
    override val spec = ToolSpec(
        name = NAME,
        description = "Takes a picture of the phone screen so you can see it, not only read its text. " +
            "Without parameters it captures the whole screen. Pass element (a number from ${PhoneScreenTool.NAME}) or x, y, width, height " +
            "in screen pixels to capture only that part. Set marks to draw the element numbers of tappable things over the picture. " +
            "Apps that block screenshots (banking, passwords) cannot be captured.",
        parameters = objectSchema(required = emptyList()) {
            integerProperty("element", "Capture only this element of the latest ${PhoneScreenTool.NAME} result", minimum = 1, maximum = MAX_ELEMENT)
            integerProperty("x", "Left edge of the area in screen pixels", minimum = 0, maximum = MAX_COORDINATE)
            integerProperty("y", "Top edge of the area in screen pixels", minimum = 0, maximum = MAX_COORDINATE)
            integerProperty("width", "Area width in screen pixels", minimum = 1, maximum = MAX_COORDINATE)
            integerProperty("height", "Area height in screen pixels", minimum = 1, maximum = MAX_COORDINATE)
            booleanProperty("marks", "Draw the element numbers over tappable elements")
            sayProperty(required = false)
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!control.isReady()) return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        val result = control.screenshot(region(arguments), arguments.optionalBoolean("marks") ?: false, arguments.sayOrNull())
        val captured = when (result) {
            is ScreenshotResult.Failed -> return ToolOutcome.error(result.reason)
            is ScreenshotResult.Captured -> result
        }
        val file = attachments.importBytes(FILE_NAME, captured.jpeg, forModel = true)
        return ToolOutcome(describe(captured), attachments = listOf(file))
    }

    private fun region(arguments: JsonObject): ScreenRegion {
        arguments.optionalInt("element")?.let { return ScreenRegion.Element(it) }
        val width = arguments.optionalInt("width") ?: return ScreenRegion.Full
        val height = arguments.optionalInt("height") ?: throw ToolArgumentException("An area needs both 'width' and 'height'")
        return ScreenRegion.Area(arguments.optionalInt("x") ?: 0, arguments.optionalInt("y") ?: 0, width, height)
    }

    private fun describe(captured: ScreenshotResult.Captured): String {
        val area = captured.region
        val scaleNote = if (captured.scale < 1f) {
            " The picture is scaled to ${"%.0f".format(captured.scale * PERCENT)}%: a point at (px, py) in it is at (${area.left} + px / ${"%.3f".format(captured.scale)}, ${area.top} + py / ${"%.3f".format(captured.scale)}) on the screen."
        } else {
            " A point at (px, py) in the picture is at (${area.left} + px, ${area.top} + py) on the screen."
        }
        return "Screenshot of the area ${area.left},${area.top} to ${area.right},${area.bottom}.$scaleNote"
    }

    companion object {
        const val NAME = "phone_screenshot"
        private const val FILE_NAME = "screenshot.jpg"
        private const val MAX_ELEMENT = 500
        private const val MAX_COORDINATE = 10_000
        private const val PERCENT = 100
    }
}

class PhoneActionTool(control: PhoneControl) : PhoneTool(control) {
    override val spec = ToolSpec(
        name = NAME,
        description = "Performs actions on the phone and reports what changed. Pass one action with 'action', or a batch with 'steps' " +
            "(up to ${PhoneRequestParser.MAX_STEPS}, each step takes the same fields as a single action plus its own say). " +
            "Element numbers come from the latest ${PhoneScreenTool.NAME} or ${NAME} result; 'target' finds an element by its text, id or role instead. " +
            "Prefer elements and targets over coordinates.",
        parameters = objectSchema(required = emptyList()) {
            enumProperty("action", "What to do", PhoneRequestParser.ACTIONS.split(", "))
            sayProperty(required = false)
            putJsonObject("steps") {
                put("type", "array")
                put("description", "Several actions in one call, each an object with the same fields as a single action plus its own say")
                putJsonObject("items") { put("type", "object") }
            }
            enumProperty("observe", "What to attach after the actions: the full screen, only the changed elements, or nothing", Observe.entries.map { it.key })
            enumProperty("on_error", "In a batch: stop on the first failed step or continue anyway", listOf("stop", "continue"))
            integerProperty("element", "Element number for tap, long_press, double_tap, type and scroll", minimum = 1, maximum = MAX_ELEMENT)
            putJsonObject("target") {
                put("type", "object")
                put(
                    "description",
                    "Element to act on, found by its text, view id or role instead of a number: " +
                        "{text, id, role}, optional exact (default false) and index (counts from 1) narrow the match",
                )
            }
            integerProperty("x", "Screen x for tap_at", minimum = 0, maximum = PhoneRequestParser.MAX_COORDINATE)
            integerProperty("y", "Screen y for tap_at", minimum = 0, maximum = PhoneRequestParser.MAX_COORDINATE)
            stringProperty("text", "Text for type (replaces the field content) and for wait_for with condition text_visible or text_gone")
            booleanProperty("submit", "Press enter after typing")
            enumProperty("direction", "Direction for scroll and scroll_to (where the content goes) and swipe (where the finger goes)", SwipeDirection.entries.map { it.key })
            enumProperty("key", "System button for press", GlobalKey.entries.map { it.key })
            stringProperty("app", "App name or package for open_app, or the app to wait for with wait_for condition app")
            integerProperty("ms", "Pause length for wait", minimum = PhoneRequestParser.MIN_WAIT_MS, maximum = PhoneRequestParser.MAX_WAIT_MS)
            integerProperty("max_swipes", "Swipe budget for scroll_to", minimum = 1, maximum = PhoneRequestParser.MAX_SWIPES)
            integerProperty("timeout_ms", "How long wait_for waits before giving up", minimum = Waiter.MIN_TIMEOUT_MS.toInt(), maximum = Waiter.MAX_TIMEOUT_MS.toInt())
            enumProperty("condition", "What wait_for waits for", listOf("text_visible", "text_gone", "app", "idle", "screen_changed"))
            putJsonObject("path") {
                put("type", "array")
                put("description", "Points for drag, like [{\"x\":100,\"y\":200},{\"x\":300,\"y\":400}]")
                putJsonObject("items") { put("type", "object") }
            }
            putJsonObject("from") { put("type", "object"); put("description", "Start point for swipe, like {\"x\":100,\"y\":200}") }
            putJsonObject("to") { put("type", "object"); put("description", "End point for swipe") }
            putJsonObject("distance") { put("type", "number"); put("description", "Swipe length as a fraction of the screen, default fits the gesture") }
            integerProperty("duration_ms", "Gesture length for swipe and drag", minimum = PhoneRequestParser.MIN_DURATION_MS, maximum = PhoneRequestParser.MAX_DURATION_MS)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!control.isReady()) return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        val report = control.run(PhoneRequestParser.parse(arguments)) ?: return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        val text = report.render()
        return if (report.failed) ToolOutcome.error(text) else ToolOutcome(text)
    }

    companion object {
        const val NAME = "phone_action"
        private const val MAX_ELEMENT = 500
    }
}

private const val SAY = "say"

private fun JsonObjectBuilder.sayProperty(required: Boolean) = stringProperty(
    SAY,
    "What you are doing right now, shown to the user next to the cursor: one short first-person sentence in the user's language, plain text" +
        if (required) "" else ", optional",
    maxLength = PhoneSay.MAX_CHARS,
)

private fun JsonObject.sayOrNull(): String? = PhoneSay.clean(optionalString(SAY))

private fun JsonObjectBuilder.enumProperty(name: String, description: String, values: List<String>) = putJsonObject(name) {
    put("type", "string")
    put("description", description)
    putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
}
