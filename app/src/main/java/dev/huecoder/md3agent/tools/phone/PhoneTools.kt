package dev.huecoder.md3agent.tools.phone

import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.control.ActionResult
import dev.huecoder.md3agent.system.control.GlobalKey
import dev.huecoder.md3agent.system.control.PhoneAction
import dev.huecoder.md3agent.system.control.PhoneControl
import dev.huecoder.md3agent.system.control.SwipeDirection
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolArgumentException
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.booleanProperty
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalBoolean
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.optionalString
import dev.huecoder.md3agent.tools.requireString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class PhoneScreenTool(private val control: PhoneControl) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads what is on the phone screen right now as a numbered list of elements with their role, text and center coordinates. " +
            "Call it before ${PhoneActionTool.NAME} so element numbers are fresh.",
        parameters = objectSchema(required = emptyList()) {},
    )

    override fun promptNote(settings: ToolSettings) =
        "You can operate the phone: read it with $NAME, then act with ${PhoneActionTool.NAME} one step at a time, checking the new screen after each step. " +
            "Never enter passwords, payment details or confirm purchases and deletions without the user saying so in this chat."

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val snapshot = control.screen() ?: return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        return ToolOutcome(snapshot.describe())
    }

    companion object {
        const val NAME = "phone_screen"
    }
}

class PhoneActionTool(private val control: PhoneControl) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Performs one action on the phone and returns the screen after it. Element numbers come from the latest " +
            "${PhoneScreenTool.NAME} or ${NAME} result. Prefer element numbers over coordinates.",
        parameters = objectSchema(required = listOf("action")) {
            enumProperty("action", "What to do", ACTIONS)
            integerProperty("element", "Element number for tap, long_press, type and scroll", minimum = 1, maximum = MAX_ELEMENT)
            integerProperty("x", "Screen x for tap_at", minimum = 0, maximum = MAX_COORDINATE)
            integerProperty("y", "Screen y for tap_at", minimum = 0, maximum = MAX_COORDINATE)
            stringProperty("text", "Text for type, replaces the field content")
            booleanProperty("submit", "Press enter after typing")
            enumProperty("direction", "Direction for scroll (where the content goes) and swipe (where the finger goes)", SwipeDirection.entries.map { it.key })
            enumProperty("key", "System button for press", GlobalKey.entries.map { it.key })
            stringProperty("app", "App name or package for open_app")
            integerProperty("ms", "Pause length for wait", minimum = 100, maximum = MAX_WAIT_MS)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!control.isReady()) return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        val action = parse(arguments)
        return when (val result = control.perform(action)) {
            is ActionResult.Failed -> ToolOutcome.error(result.reason)
            is ActionResult.Done -> {
                if (action !is PhoneAction.Wait) delay(SETTLE_MS)
                val screen = control.screen()?.describe() ?: PhoneControl.NOT_CONNECTED
                ToolOutcome(result.note + "\n\n" + screen)
            }
        }
    }

    private fun parse(arguments: JsonObject): PhoneAction {
        val element = arguments.optionalInt("element")
        return when (val name = arguments.requireString("action")) {
            "tap" -> PhoneAction.Tap(element ?: missing("element"))
            "long_press" -> PhoneAction.LongPress(element ?: missing("element"))
            "tap_at" -> PhoneAction.TapAt(arguments.optionalInt("x") ?: missing("x"), arguments.optionalInt("y") ?: missing("y"))
            "type" -> PhoneAction.Type(element, arguments.requireString("text"), arguments.optionalBoolean("submit") ?: false)
            "scroll" -> PhoneAction.Scroll(element, direction(arguments))
            "swipe" -> PhoneAction.Swipe(direction(arguments))
            "press" -> PhoneAction.Press(GlobalKey.of(arguments.optionalString("key")) ?: missing("key"))
            "open_app" -> PhoneAction.OpenApp(arguments.requireString("app"))
            "wait" -> PhoneAction.Wait((arguments.optionalInt("ms") ?: DEFAULT_WAIT_MS).coerceIn(MIN_WAIT_MS, MAX_WAIT_MS).toLong())
            else -> throw ToolArgumentException("Unknown action '$name', use one of ${ACTIONS.joinToString()}")
        }
    }

    private fun direction(arguments: JsonObject) = SwipeDirection.of(arguments.optionalString("direction")) ?: missing("direction")

    private fun missing(name: String): Nothing = throw ToolArgumentException("This action needs '$name'")

    companion object {
        const val NAME = "phone_action"
        private val ACTIONS = listOf("tap", "long_press", "tap_at", "type", "scroll", "swipe", "press", "open_app", "wait")
        private const val SETTLE_MS = 700L
        private const val DEFAULT_WAIT_MS = 1_500
        private const val MIN_WAIT_MS = 100
        private const val MAX_WAIT_MS = 10_000
        private const val MAX_ELEMENT = 500
        private const val MAX_COORDINATE = 10_000
    }
}

private fun JsonObjectBuilder.enumProperty(name: String, description: String, values: List<String>) = putJsonObject(name) {
    put("type", "string")
    put("description", description)
    putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
}
