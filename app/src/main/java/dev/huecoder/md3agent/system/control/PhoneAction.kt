package dev.huecoder.md3agent.system.control

enum class SwipeDirection(val key: String, val dx: Int, val dy: Int) {
    UP("up", 0, -1),
    DOWN("down", 0, 1),
    LEFT("left", -1, 0),
    RIGHT("right", 1, 0),
    UP_LEFT("up_left", -1, -1),
    UP_RIGHT("up_right", 1, -1),
    DOWN_LEFT("down_left", -1, 1),
    DOWN_RIGHT("down_right", 1, 1),
    ;

    val isDiagonal: Boolean get() = dx != 0 && dy != 0

    val opposite: SwipeDirection
        get() = entries.first { it.dx == -dx && it.dy == -dy }

    companion object {
        val straight: List<SwipeDirection> = listOf(UP, DOWN, LEFT, RIGHT)

        fun of(key: String?): SwipeDirection? = entries.firstOrNull { it.key == key }
    }
}

enum class GlobalKey(val key: String) {
    BACK("back"),
    HOME("home"),
    RECENTS("recents"),
    NOTIFICATIONS("notifications"),
    QUICK_SETTINGS("quick_settings"),
    LOCK_SCREEN("lock_screen"),
    SCREENSHOT("screenshot"),
    ;

    companion object {
        fun of(key: String?): GlobalKey? = entries.firstOrNull { it.key == key }
    }
}

data class ScreenPoint(val x: Int, val y: Int)

sealed interface Target {
    data class Numbered(val number: Int) : Target
    data class Selected(val selector: ElementSelector) : Target
}

data class SwipeSpec(
    val direction: SwipeDirection? = null,
    val from: ScreenPoint? = null,
    val to: ScreenPoint? = null,
    val distance: Float? = null,
    val durationMs: Long? = null,
)

sealed interface PhoneAction {
    data class Tap(val target: Target) : PhoneAction
    data class LongPress(val target: Target) : PhoneAction
    data class DoubleTap(val target: Target) : PhoneAction
    data class TapAt(val x: Int, val y: Int) : PhoneAction
    data class Type(val target: Target?, val text: String, val submit: Boolean) : PhoneAction
    data class Scroll(val target: Target?, val direction: SwipeDirection) : PhoneAction
    data class ScrollTo(val selector: ElementSelector, val direction: SwipeDirection?, val maxSwipes: Int) : PhoneAction
    data class Swipe(val spec: SwipeSpec) : PhoneAction
    data class Drag(val path: List<ScreenPoint>, val durationMs: Long?) : PhoneAction
    data class Press(val key: GlobalKey) : PhoneAction
    data class OpenApp(val name: String) : PhoneAction
    data class Wait(val millis: Long) : PhoneAction
    data class WaitFor(val condition: WaitCondition, val timeoutMs: Long) : PhoneAction
}

enum class Observe(val key: String) {
    SCREEN("screen"),
    CHANGES("changes"),
    NONE("none"),
    ;

    companion object {
        fun of(key: String?): Observe? = entries.firstOrNull { it.key == key }
    }
}

data class PhoneStep(val action: PhoneAction, val say: String? = null)

data class PhoneRequest(
    val steps: List<PhoneStep>,
    val observe: Observe = Observe.SCREEN,
    val stopOnError: Boolean = true,
)

sealed interface ActionResult {
    data class Done(val note: String) : ActionResult
    data class Failed(val reason: String) : ActionResult
}

data class StepReport(val title: String, val result: ActionResult) {
    val failed: Boolean get() = result is ActionResult.Failed

    val text: String
        get() = when (result) {
            is ActionResult.Done -> result.note
            is ActionResult.Failed -> "failed: ${result.reason}"
        }
}

data class PhoneReport(
    val steps: List<StepReport>,
    val screenChanged: Boolean?,
    val observed: String?,
    val hint: String?,
) {
    val failed: Boolean get() = steps.any { it.failed }

    fun render(): String = buildString {
        if (steps.size == 1) {
            append(steps.single().text)
        } else {
            steps.forEachIndexed { index, step -> appendLine("${index + 1}. ${step.title} → ${step.text}") }
        }
        screenChanged?.let { append("\nScreen changed: ").append(if (it) "yes" else "no") }
        hint?.let { append("\n").append(it) }
        observed?.let { append("\n\n").append(it) }
    }.trim()
}

fun PhoneAction.title(): String = when (this) {
    is PhoneAction.Tap -> "tap ${target.title()}"
    is PhoneAction.LongPress -> "long_press ${target.title()}"
    is PhoneAction.DoubleTap -> "double_tap ${target.title()}"
    is PhoneAction.TapAt -> "tap_at $x,$y"
    is PhoneAction.Type -> "type \"${text.take(TITLE_TEXT_CHARS)}\""
    is PhoneAction.Scroll -> "scroll ${direction.key}"
    is PhoneAction.ScrollTo -> "scroll_to ${selector.describe()}"
    is PhoneAction.Swipe -> "swipe ${spec.direction?.key ?: "from-to"}"
    is PhoneAction.Drag -> "drag ${path.size} points"
    is PhoneAction.Press -> "press ${key.key}"
    is PhoneAction.OpenApp -> "open_app $name"
    is PhoneAction.Wait -> "wait $millis ms"
    is PhoneAction.WaitFor -> "wait_for ${condition::class.simpleName}"
}

private fun Target.title(): String = when (this) {
    is Target.Numbered -> "[$number]"
    is Target.Selected -> selector.describe()
}

private const val TITLE_TEXT_CHARS = 30
