package com.mwagent.tools.phone

import com.mwagent.system.control.ElementSelector
import com.mwagent.system.control.GlobalKey
import com.mwagent.system.control.Observe
import com.mwagent.system.control.PhoneAction
import com.mwagent.system.control.PhoneRequest
import com.mwagent.system.control.PhoneSay
import com.mwagent.system.control.PhoneStep
import com.mwagent.system.control.ScreenPoint
import com.mwagent.system.control.SwipeDirection
import com.mwagent.system.control.SwipeSpec
import com.mwagent.system.control.Target
import com.mwagent.system.control.WaitCondition
import com.mwagent.system.control.Waiter
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

object PhoneRequestParser {
    const val ACTIONS = "tap, long_press, double_tap, tap_at, type, scroll, scroll_to, swipe, drag, press, open_app, wait, wait_for"
    val ACTION_NAMES: List<String> = ACTIONS.split(", ")
    const val MAX_STEPS = 10
    const val MAX_PATH_POINTS = 16
    const val MIN_WAIT_MS = 100
    const val MAX_WAIT_MS = 10_000
    const val DEFAULT_WAIT_MS = 1_500
    const val DEFAULT_MAX_SWIPES = 12
    const val MAX_SWIPES = 30
    const val MIN_DURATION_MS = 80
    const val MAX_DURATION_MS = 3_000
    const val MIN_DISTANCE = 0.1f
    const val MAX_DISTANCE = 0.95f
    const val MAX_COORDINATE = 10_000
    private const val TARGET = "target"

    fun parse(arguments: JsonObject): PhoneRequest {
        val observe = arguments.optionalString("observe")?.let { Observe.of(it) ?: throw ToolArgumentException("observe must be screen, changes or none") } ?: Observe.SCREEN
        val stopOnError = when (arguments.optionalString("on_error")) {
            null, "stop" -> true
            "continue" -> false
            else -> throw ToolArgumentException("on_error must be stop or continue")
        }
        val steps = arguments["steps"] as? JsonArray
        if (steps != null) {
            if (steps.isEmpty() || steps.size > MAX_STEPS) throw ToolArgumentException("steps takes 1 to $MAX_STEPS steps")
            return PhoneRequest(steps.mapIndexed { index, item -> step(item as? JsonObject ?: throw ToolArgumentException("Step ${index + 1} must be an object")) }, observe, stopOnError)
        }
        if (arguments.optionalString("action") == null) throw ToolArgumentException("Pass 'action' or 'steps'")
        return PhoneRequest(listOf(step(arguments)), observe, stopOnError)
    }

    fun step(arguments: JsonObject): PhoneStep {
        if (arguments.containsKey("steps")) throw ToolArgumentException("A step cannot contain steps")
        return PhoneStep(action(arguments), PhoneSay.clean(arguments.optionalString("say")))
    }

    private fun action(arguments: JsonObject): PhoneAction = when (val name = arguments.requireString("action")) {
        "tap" -> PhoneAction.Tap(target(arguments, name))
        "long_press" -> PhoneAction.LongPress(target(arguments, name))
        "double_tap" -> PhoneAction.DoubleTap(target(arguments, name))
        "tap_at" -> PhoneAction.TapAt(coordinate(arguments, "x"), coordinate(arguments, "y"))
        "type" -> PhoneAction.Type(optionalTarget(arguments), arguments.requireString("text"), arguments.optionalBoolean("submit") ?: false)
        "scroll" -> PhoneAction.Scroll(optionalTarget(arguments), scrollDirection(arguments))
        "scroll_to" -> PhoneAction.ScrollTo(
            selector(arguments) ?: throw ToolArgumentException("scroll_to needs a 'target' with text, id or role"),
            arguments.optionalString("direction")?.let { SwipeDirection.of(it)?.takeUnless(SwipeDirection::isDiagonal) ?: throw ToolArgumentException("scroll_to direction is up, down, left or right") },
            (arguments.optionalInt("max_swipes") ?: DEFAULT_MAX_SWIPES).coerceIn(1, MAX_SWIPES),
        )
        "swipe" -> PhoneAction.Swipe(swipeSpec(arguments))
        "drag" -> PhoneAction.Drag(path(arguments), duration(arguments))
        "press" -> PhoneAction.Press(GlobalKey.of(arguments.optionalString("key")) ?: missing("key"))
        "open_app" -> PhoneAction.OpenApp(arguments.requireString("app"))
        "wait" -> PhoneAction.Wait((arguments.optionalInt("ms") ?: DEFAULT_WAIT_MS).coerceIn(MIN_WAIT_MS, MAX_WAIT_MS).toLong())
        "wait_for" -> waitFor(arguments)
        else -> throw ToolArgumentException("Unknown action '$name', use one of $ACTIONS")
    }

    private fun waitFor(arguments: JsonObject): PhoneAction {
        val timeout = arguments.optionalInt("timeout_ms")
        if (timeout == null || timeout !in Waiter.MIN_TIMEOUT_MS..Waiter.MAX_TIMEOUT_MS) {
            throw ToolArgumentException("wait_for needs timeout_ms between ${Waiter.MIN_TIMEOUT_MS} and ${Waiter.MAX_TIMEOUT_MS}")
        }
        val condition = when (val name = arguments.requireString("condition")) {
            "text_visible" -> WaitCondition.TextVisible(arguments.requireString("text"))
            "text_gone" -> WaitCondition.TextGone(arguments.requireString("text"))
            "app" -> WaitCondition.AppInFront(arguments.requireString("app"))
            "idle" -> WaitCondition.Idle()
            "screen_changed" -> WaitCondition.ScreenChanged
            else -> throw ToolArgumentException("Unknown condition '$name', use text_visible, text_gone, app, idle or screen_changed")
        }
        return PhoneAction.WaitFor(condition, timeout.toLong())
    }

    private fun target(arguments: JsonObject, action: String): Target =
        optionalTarget(arguments) ?: throw ToolArgumentException("$action needs 'element' (a number from the screen) or 'target' with text, id or role")

    private fun optionalTarget(arguments: JsonObject): Target? =
        arguments.optionalInt("element")?.let(Target::Numbered) ?: selector(arguments)?.let(Target::Selected)

    fun selector(arguments: JsonObject, key: String = TARGET): ElementSelector? {
        val target = arguments[key] as? JsonObject ?: return null
        val selector = ElementSelector(
            text = target.optionalString("text"),
            id = target.optionalString("id"),
            role = target.optionalString("role"),
            exact = target.optionalBoolean("exact") ?: false,
            index = target.optionalInt("index")?.also { if (it < 1) throw ToolArgumentException("index counts from 1") },
        )
        if (selector.isBlank) throw ToolArgumentException("$key needs text, id or role")
        return selector
    }

    private fun scrollDirection(arguments: JsonObject): SwipeDirection =
        SwipeDirection.of(arguments.optionalString("direction"))?.takeUnless(SwipeDirection::isDiagonal)
            ?: throw ToolArgumentException("scroll needs direction up, down, left or right")

    private fun swipeSpec(arguments: JsonObject): SwipeSpec {
        val from = point(arguments["from"])
        val to = point(arguments["to"])
        val direction = arguments.optionalString("direction")?.let { SwipeDirection.of(it) ?: throw ToolArgumentException("Unknown swipe direction '$it'") }
        if ((from == null) != (to == null)) throw ToolArgumentException("swipe needs both 'from' and 'to', or a direction")
        if (from == null && direction == null) throw ToolArgumentException("swipe needs a direction, or 'from' and 'to'")
        return SwipeSpec(direction, from, to, distance(arguments), duration(arguments))
    }

    private fun path(arguments: JsonObject): List<ScreenPoint> {
        val points = (arguments["path"] as? JsonArray)?.mapNotNull { point(it) }.orEmpty()
        if (points.size < 2 || points.size > MAX_PATH_POINTS) throw ToolArgumentException("drag needs a path of 2 to $MAX_PATH_POINTS points like [{\"x\":100,\"y\":200},{\"x\":300,\"y\":400}]")
        return points
    }

    private fun point(element: kotlinx.serialization.json.JsonElement?): ScreenPoint? {
        val obj = element as? JsonObject ?: return null
        return ScreenPoint(coordinate(obj, "x"), coordinate(obj, "y"))
    }

    private fun coordinate(arguments: JsonObject, name: String): Int =
        (arguments.optionalInt(name) ?: missing(name)).also { if (it !in 0..MAX_COORDINATE) throw ToolArgumentException("'$name' must be between 0 and $MAX_COORDINATE") }

    private fun distance(arguments: JsonObject): Float? =
        (arguments["distance"] as? JsonPrimitive)?.doubleOrNull?.toFloat()?.coerceIn(MIN_DISTANCE, MAX_DISTANCE)

    private fun duration(arguments: JsonObject): Long? = arguments.optionalInt("duration_ms")?.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)?.toLong()

    private fun missing(name: String): Nothing = throw ToolArgumentException("This action needs '$name'")
}
