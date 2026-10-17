package com.mwagent.tools.phone

import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.control.PhoneControl
import com.mwagent.system.control.PhoneSay
import com.mwagent.system.control.ScreenRegion
import com.mwagent.system.control.ScreenshotResult
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolContext
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.booleanProperty
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import kotlinx.serialization.json.JsonObject

abstract class PhoneTool(protected val control: PhoneControl) : Tool {
    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        control.session.begin(context.chatId)
        return execute(arguments, settings)
    }
}

class PhoneScreenTool(control: PhoneControl) : PhoneTool(control) {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads what is on the phone screen right now as a numbered list of elements with their role, text, state and center coordinates, " +
            "and shows which way a list can still scroll. Pass find to list only the elements that match, with their ids and bounds. " +
            "Call it before ${PhoneActionTool.NAME} so element numbers are fresh.",
        parameters = objectSchema(required = emptyList()) {
            booleanProperty("detailed", "Also list each element's exact bounds, view class and resource id, like a trimmed UI hierarchy dump")
            selectorProperty(FIND, "List only elements that match {text, id, role}, optional exact and index, instead of the whole screen")
            sayProperty(required = false)
        },
    )

    override fun promptNote(settings: ToolSettings) = listOf(
        "You can operate the phone. Work in a loop: look with $NAME, act with ${PhoneActionTool.NAME} (it returns the new screen), read that screen, repeat until the goal is reached.",
        "Every phone call carries a 'say' phrase that the user sees next to the cursor: one short sentence, first person, in the user's language, about what you do right now, like \"Opening Settings\" or \"Scrolling to find Wi-Fi\". Plain text, no markdown, at most ${PhoneSay.MAX_CHARS} characters.",
        "Element numbers go stale as soon as the screen changes, so take them from the screen the last action returned, or address elements by 'target' (text, id or role). " +
            "Use scroll_to with a target to reach something off screen instead of scrolling blindly, wait_for with an explicit timeout_ms after anything that loads instead of guessing a pause, and 'steps' to batch a sequence you are sure about.",
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
        description = "Performs actions on the phone and reports whether the screen changed, then lists the new screen. " +
            "Actions: ${PhoneRequestParser.ACTIONS}. Pass one with 'action', or up to ${PhoneRequestParser.MAX_STEPS} in a batch with 'steps'. " +
            "Element numbers come from the latest ${PhoneScreenTool.NAME} or $NAME result; 'target' finds an element by its text, id or role instead. " +
            "Prefer elements and targets over coordinates.",
        parameters = PhoneActionSchema.parameters(),
    )

    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs = TIMEOUT_MS

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!control.isReady()) return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        val report = control.run(PhoneRequestParser.parse(arguments)) ?: return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        val text = report.render()
        return if (report.failed) ToolOutcome.error(text) else ToolOutcome(text)
    }

    companion object {
        const val NAME = "phone_action"
        private const val TIMEOUT_MS = 120_000L
    }
}

private fun JsonObject.sayOrNull(): String? = PhoneSay.clean(optionalString(SAY))
