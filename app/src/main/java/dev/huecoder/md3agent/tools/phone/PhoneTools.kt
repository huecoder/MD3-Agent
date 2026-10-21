package dev.huecoder.md3agent.tools.phone

import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.control.Observe
import dev.huecoder.md3agent.system.control.PhoneControl
import dev.huecoder.md3agent.system.control.PhoneSay
import dev.huecoder.md3agent.system.control.ScreenRegion
import dev.huecoder.md3agent.system.control.ScreenshotResult
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
import kotlinx.serialization.json.JsonObject

abstract class PhoneTool(protected val control: PhoneControl) : Tool {
    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        control.session.begin(context.chatId)
        arguments.optionalBoolean(RETURN_TO_CHAT)?.let(control.session::openChatWhenDone)
        return execute(arguments, settings)
    }
}

class PhoneScreenTool(control: PhoneControl) : PhoneTool(control) {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads what is on the phone screen right now as a numbered list of elements with their role, text, state and center coordinates, " +
            "and marks each list with the directions it can still scroll, like 'scroll down/up'; pass such a word as the direction of a scroll. Pass find to list only the elements that match, with their ids and bounds. " +
            "Call it before ${PhoneActionTool.NAME} so element numbers are fresh.",
        parameters = objectSchema(required = emptyList()) {
            booleanProperty("detailed", "Also list each element's exact bounds, view class and resource id, like a trimmed UI hierarchy dump")
            selectorProperty(FIND, "List only elements that match {text, id, role}, optional exact and index, instead of the whole screen")
            sayProperty(required = false)
            returnProperty()
        },
    )

    override fun promptNote(settings: ToolSettings) = listOf(
        "You can operate the phone. Work in a loop: look with $NAME, act with ${PhoneActionTool.NAME}, read what it returns, repeat until the goal is reached. " +
            "An action returns only what changed (new, changed and gone elements, '+N changed' when more changed than is listed), or the whole screen after open_app, home, back or any move to another screen: do not call $NAME again to check the result. " +
            "Call $NAME only when you see '+N changed', 'numbers moved' or need the elements you cannot see in the result. Pass observe=screenshot to get a picture with the result, it also arrives by itself when the app exposes almost no elements (games, maps, canvases).",
        "Every phone call carries a 'say' phrase that the user sees next to the cursor: one short sentence, first person, in the user's language, about what you do right now, like \"Opening Settings\" or \"Scrolling to find Wi-Fi\". Plain text, no markdown, at most ${PhoneSay.MAX_CHARS} characters.",
        "Element numbers go stale as soon as the screen changes, so take them from the screen the last action returned, or address elements by 'target' (text, id or role). " +
            "Use scroll_to with a target to reach something off screen instead of scrolling blindly, wait_for with an explicit timeout_ms after anything that loads instead of guessing a pause, and 'steps' to batch a sequence you are sure about.",
        "Directions: scroll and scroll_to take where you want to look, 'down' shows what is further down the page or list and 'up' goes back toward the top. swipe takes where the finger moves, so swipe 'up' shows what is below and swipe 'down' shows what is above: scroll down and swipe up do the same thing. " +
            "Use scroll or scroll_to for lists and pages, and swipe only for gestures like pulling a shade or paging a carousel. A list marked 'scroll down/up' has more content in both of those directions. After a scroll read the new screen: if it looks the same you reached the end, so do not repeat it.",
        "When you finish, the user stays on the screen you left and is not taken back to this chat unless you pass return_to_chat=true on a phone call (the latest value you pass counts). " +
            "Pass it after a long task with many steps, when you collected something the user has to read, when you could not finish, or when you need an answer or a decision. " +
            "Do not pass it when the screen you reached is the point of the request, like opening Settings, the gallery, an app, a contact or a page, or flipping a switch: then stay there and keep your reply to one short line.",
        "If the context block says the user opened you over an app, that app is already on screen: act in it, and do not close it, go home or open it again unless the user asks.",
        "If an action fails or changes nothing, do not repeat it more than twice: pick another element, scroll, or look at the pixels with ${PhoneScreenshotTool.NAME} (games, canvases and custom views have no text list).",
        "Never enter passwords or payment details, and never confirm purchases or deletions, unless the user said so in this chat.",
    ).joinToString("\n")

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val find = PhoneRequestParser.selector(arguments, FIND)
        val reading = control.read(arguments.sayOrNull(), arguments.optionalBoolean("detailed") ?: false, find)
        return if (reading == null) ToolOutcome.error(PhoneControl.NOT_CONNECTED) else ToolOutcome(reading.text)
    }

    companion object {
        const val NAME = "phone_screen"
        private const val FIND = "find"
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
            returnProperty()
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
        return ToolOutcome(describeScreenshot(captured), attachments = listOf(file))
    }

    private fun region(arguments: JsonObject): ScreenRegion {
        arguments.optionalInt("element")?.let { return ScreenRegion.Element(it) }
        val width = arguments.optionalInt("width") ?: return ScreenRegion.Full
        val height = arguments.optionalInt("height") ?: throw ToolArgumentException("An area needs both 'width' and 'height'")
        return ScreenRegion.Area(arguments.optionalInt("x") ?: 0, arguments.optionalInt("y") ?: 0, width, height)
    }

    companion object {
        const val NAME = "phone_screenshot"
        private const val FILE_NAME = "screenshot.jpg"
        private const val MAX_ELEMENT = 500
        private const val MAX_COORDINATE = 10_000
    }
}

class PhoneActionTool(control: PhoneControl, private val attachments: AttachmentStore) : PhoneTool(control) {
    override val spec = ToolSpec(
        name = NAME,
        description = "Performs actions on the phone, then reports what changed on the screen: new, changed and gone elements, with '+N changed' when more changed than fits. " +
            "After open_app, home, back or any action that moves to another screen it returns the whole screen like ${PhoneScreenTool.NAME}, so you do not need to read it again. " +
            "Actions: ${PhoneRequestParser.ACTIONS}. Pass one with 'action', or up to ${PhoneRequestParser.MAX_STEPS} in a batch with 'steps'. " +
            "Element numbers come from the latest ${PhoneScreenTool.NAME} or $NAME result; 'target' finds an element by its text, id or role instead. " +
            "Prefer elements and targets over coordinates. To move through a list or a page use scroll or scroll_to, and keep swipe for gestures like pulling a shade or paging a carousel.",
        parameters = PhoneActionSchema.parameters(),
    )

    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs = TIMEOUT_MS

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!control.isReady()) return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        val request = PhoneRequestParser.parse(arguments)
        val report = control.run(request) ?: return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        val text = report.render()
        if (report.failed) return ToolOutcome.error(text)
        val wantsPicture = request.observe == Observe.SCREENSHOT || (report.sparseScreen && request.observe != Observe.NONE)
        return if (wantsPicture) withScreenshot(text) else ToolOutcome(text)
    }

    private suspend fun withScreenshot(text: String): ToolOutcome = when (val result = control.screenshot(ScreenRegion.Full, marks = true)) {
        is ScreenshotResult.Failed -> ToolOutcome("$text\n\nNo screenshot: ${result.reason}")
        is ScreenshotResult.Captured -> {
            val file = attachments.importBytes(SCREENSHOT_FILE_NAME, result.jpeg, forModel = true)
            ToolOutcome("$text\n\n${describeScreenshot(result)}", attachments = listOf(file))
        }
    }

    companion object {
        const val NAME = "phone_action"
        private const val TIMEOUT_MS = 120_000L
        private const val SCREENSHOT_FILE_NAME = "screenshot.jpg"
    }
}

private fun describeScreenshot(captured: ScreenshotResult.Captured): String {
    val area = captured.region
    val scaleNote = if (captured.scale < 1f) {
        " The picture is scaled to ${"%.0f".format(captured.scale * SHOT_PERCENT)}%: a point at (px, py) in it is at (${area.left} + px / ${"%.3f".format(captured.scale)}, ${area.top} + py / ${"%.3f".format(captured.scale)}) on the screen."
    } else {
        " A point at (px, py) in the picture is at (${area.left} + px, ${area.top} + py) on the screen."
    }
    return "Screenshot of the area ${area.left},${area.top} to ${area.right},${area.bottom}.$scaleNote"
}

private const val SHOT_PERCENT = 100

private fun JsonObject.sayOrNull(): String? = PhoneSay.clean(optionalString(SAY))
