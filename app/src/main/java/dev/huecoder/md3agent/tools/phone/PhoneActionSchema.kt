package dev.huecoder.md3agent.tools.phone

import dev.huecoder.md3agent.system.control.GlobalKey
import dev.huecoder.md3agent.system.control.Observe
import dev.huecoder.md3agent.system.control.PhoneSay
import dev.huecoder.md3agent.system.control.SwipeDirection
import dev.huecoder.md3agent.system.control.Waiter
import dev.huecoder.md3agent.tools.objectSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object PhoneActionSchema {
    const val ACTION = "action"
    const val STEPS = "steps"
    val WAIT_CONDITIONS = listOf("text_visible", "text_gone", "app", "idle", "screen_changed")
    val ERROR_POLICIES = listOf("stop", "continue")

    fun parameters(): JsonObject = objectSchema(required = emptyList()) {
        enumProperty(ACTION, "What to do", PhoneRequestParser.ACTION_NAMES)
        sayProperty(required = false)
        stepsProperty()
        enumProperty("observe", "What to attach after the actions: the full screen, only the changed elements, or nothing", Observe.entries.map { it.key })
        enumProperty("on_error", "In a batch: stop on the first failed step or continue anyway", ERROR_POLICIES)
        actionFields(documented = true)
    }

    private fun JsonObjectBuilder.stepsProperty() = putJsonObject(STEPS) {
        put("type", "array")
        put(
            "description",
            "Several actions in one call instead of 'action', each step takes the fields below plus its own say. " +
                "Use it for sequences you are sure about, like tap a field, type, press enter. A failed step stops the batch unless on_error is continue. " +
                "Element numbers in later steps still refer to the screen before the batch, so prefer 'target' after steps that change the screen.",
        )
        put("minItems", 1)
        put("maxItems", PhoneRequestParser.MAX_STEPS)
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                enumProperty(ACTION, null, PhoneRequestParser.ACTION_NAMES)
                sayProperty(required = false, documented = false)
                actionFields(documented = false)
            }
            putJsonArray("required") { add(JsonPrimitive(ACTION)) }
        }
    }

    private fun JsonObjectBuilder.actionFields(documented: Boolean) {
        fun note(text: String): String? = text.takeIf { documented }
        integerProperty("element", note("Element number for tap, long_press, double_tap, type and scroll, from the latest ${PhoneScreenTool.NAME} result"), 1, MAX_ELEMENT)
        selectorProperty("target", note("Element to act on, found by its text, view id or role instead of a number. Safer than a number when the screen may have changed"))
        integerProperty("x", note("Screen x for tap_at"), 0, PhoneRequestParser.MAX_COORDINATE)
        integerProperty("y", note("Screen y for tap_at"), 0, PhoneRequestParser.MAX_COORDINATE)
        stringProperty("text", note("Text for type (replaces the field content), and the text to watch for in wait_for with text_visible or text_gone"))
        booleanProperty("submit", note("Press enter after typing, a send or search button is tapped when enter does not work"))
        enumProperty(
            "direction",
            note("scroll: where the content goes. scroll_to: which way to look first. swipe: where the finger goes, diagonals allowed"),
            SwipeDirection.entries.map { it.key },
        )
        enumProperty("key", note("System button for press"), GlobalKey.entries.map { it.key })
        stringProperty("app", note("App name or package for open_app, or the app to wait for in wait_for with condition app"))
        integerProperty("ms", note("Pause length for wait"), PhoneRequestParser.MIN_WAIT_MS, PhoneRequestParser.MAX_WAIT_MS)
        integerProperty("max_swipes", note("Swipe budget for scroll_to, default ${PhoneRequestParser.DEFAULT_MAX_SWIPES}"), 1, PhoneRequestParser.MAX_SWIPES)
        enumProperty("condition", note("What wait_for waits for"), WAIT_CONDITIONS)
        integerProperty(
            "timeout_ms",
            note("Required for wait_for: how long to wait before giving up"),
            Waiter.MIN_TIMEOUT_MS.toInt(),
            Waiter.MAX_TIMEOUT_MS.toInt(),
        )
        pointProperty("from", note("Start point for swipe, like {\"x\":100,\"y\":200}, instead of a direction"))
        pointProperty("to", note("End point for swipe"))
        putJsonObject("path") {
            put("type", "array")
            note("Points for drag, 2 to ${PhoneRequestParser.MAX_PATH_POINTS}, like [{\"x\":100,\"y\":200},{\"x\":300,\"y\":400}]")?.let { put("description", it) }
            put("maxItems", PhoneRequestParser.MAX_PATH_POINTS)
            putJsonObject("items") { pointSchema() }
        }
        putJsonObject("distance") {
            put("type", "number")
            note("Swipe length as a fraction of the screen from ${PhoneRequestParser.MIN_DISTANCE} to ${PhoneRequestParser.MAX_DISTANCE}, the default fits the gesture")?.let { put("description", it) }
            put("minimum", PhoneRequestParser.MIN_DISTANCE)
            put("maximum", PhoneRequestParser.MAX_DISTANCE)
        }
        integerProperty(
            "duration_ms",
            note("Gesture length for swipe and drag, a slower swipe moves the content less far"),
            PhoneRequestParser.MIN_DURATION_MS,
            PhoneRequestParser.MAX_DURATION_MS,
        )
    }

    private const val MAX_ELEMENT = 500
}

internal const val SAY = "say"

internal fun JsonObjectBuilder.sayProperty(required: Boolean, documented: Boolean = true) = putJsonObject(SAY) {
    put("type", "string")
    if (documented) {
        put(
            "description",
            "What you are doing right now, shown to the user next to the cursor: one short first-person sentence in the user's language, plain text" +
                if (required) "" else ", optional",
        )
    }
    put("maxLength", PhoneSay.MAX_CHARS)
}

internal fun JsonObjectBuilder.selectorProperty(name: String, description: String?) = putJsonObject(name) {
    put("type", "object")
    description?.let { put("description", it) }
    putJsonObject("properties") {
        putJsonObject("text") {
            put("type", "string")
            put("description", "Visible text or content description, matched as a part and ignoring case unless exact is true")
        }
        putJsonObject("id") {
            put("type", "string")
            put("description", "View id without the package, the id= value that ${PhoneScreenTool.NAME} prints with detailed or find")
        }
        putJsonObject("role") {
            put("type", "string")
            put("description", "Role as printed on the screen: Button, Input, Switch, Checkbox, List, Image, Item or Text")
        }
        putJsonObject("exact") {
            put("type", "boolean")
            put("description", "Match the whole text instead of a part, default false")
        }
        putJsonObject("index") {
            put("type", "integer")
            put("minimum", 1)
            put("description", "Which match to take when several fit, counting from 1")
        }
    }
}

internal fun JsonObjectBuilder.enumProperty(name: String, description: String?, values: List<String>) = putJsonObject(name) {
    put("type", "string")
    description?.let { put("description", it) }
    putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
}

private fun JsonObjectBuilder.integerProperty(name: String, description: String?, minimum: Int, maximum: Int) = putJsonObject(name) {
    put("type", "integer")
    description?.let { put("description", it) }
    put("minimum", minimum)
    put("maximum", maximum)
}

private fun JsonObjectBuilder.stringProperty(name: String, description: String?) = putJsonObject(name) {
    put("type", "string")
    description?.let { put("description", it) }
}

private fun JsonObjectBuilder.booleanProperty(name: String, description: String?) = putJsonObject(name) {
    put("type", "boolean")
    description?.let { put("description", it) }
}

private fun JsonObjectBuilder.pointProperty(name: String, description: String?) = putJsonObject(name) {
    description?.let { put("description", it) }
    pointSchema()
}

private fun JsonObjectBuilder.pointSchema() {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("x") { put("type", "integer") }
        putJsonObject("y") { put("type", "integer") }
    }
    putJsonArray("required") {
        add(JsonPrimitive("x"))
        add(JsonPrimitive("y"))
    }
}
