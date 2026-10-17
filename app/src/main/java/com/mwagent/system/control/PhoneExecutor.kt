package com.mwagent.system.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.annotation.StringRes
import com.mwagent.R
import kotlinx.coroutines.delay

internal class PhoneExecutor(
    private val context: Context,
    private val session: PhoneUseSession,
    private val reader: ScreenReader,
    private val gestures: PhoneGestures,
    private val injector: InputInjector,
) {
    private val repeats = RepeatGuard()

    suspend fun run(service: AccessibilityService, request: PhoneRequest): PhoneReport {
        val reports = mutableListOf<StepReport>()
        val beforeElements = if (request.observe == Observe.CHANGES) reader.snapshot(service).elements else emptyList()
        val before = reader.probe(service)
        for ((index, step) in request.steps.withIndex()) {
            val result = runStep(service, step)
            reports += StepReport(step.action.title(), result)
            if (result is ActionResult.Failed && request.stopOnError) break
            if (index < request.steps.lastIndex && step.action.mayChangeScreen()) settle(service, SETTLE_BETWEEN_MS)
        }
        if (request.steps.any { it.action.mayChangeScreen() }) settle(service, SETTLE_MAX_MS)
        val snapshot = if (request.observe == Observe.NONE) null else reader.snapshot(service)
        val after = snapshot?.probe ?: reader.probe(service)
        val changed = after.fingerprint != before.fingerprint
        val hint = LOOP_HINT.takeIf { repeats.record(request.steps.joinToString("|") { it.action.title() }, changed) }
        return PhoneReport(reports, changed, snapshot?.let { observation(request.observe, it, beforeElements, changed) }, hint)
    }

    suspend fun read(service: AccessibilityService, detailed: Boolean, find: ElementSelector?): ScreenReading {
        val leftOwnApp = reader.isOwnAppInFront(service)
        if (leftOwnApp) leaveOwnApp(service)
        val snapshot = reader.snapshot(service)
        val description = snapshot.describe(detailed, find)
        return ScreenReading(snapshot, if (leftOwnApp) "$HOME_FIRST_NOTE\n$description" else description)
    }

    private fun observation(observe: Observe, snapshot: ScreenSnapshot, before: List<ScreenElement>, changed: Boolean): String = when (observe) {
        Observe.SCREEN -> snapshot.describe()
        Observe.CHANGES -> {
            val changes = ScreenChanges.between(before, snapshot.elements)
            "App: ${snapshot.packageName ?: "unknown"}\n" + if (changed && !changes.isEmpty) changes.describe() else "No visible changes"
        }
        Observe.NONE -> ""
    }

    private suspend fun runStep(service: AccessibilityService, step: PhoneStep): ActionResult {
        val action = step.action
        val say = step.say
        val leftOwnApp = action.touchesScreen() && reader.isOwnAppInFront(service)
        if (leftOwnApp) {
            leaveOwnApp(service)
            if (action.usesNumbers()) return ActionResult.Failed("The assistant's own app was in front, so I went to the home screen. Read the screen again before using element numbers.")
        }
        val result = perform(service, action, say)
        return if (leftOwnApp) result.withNote(HOME_FIRST_NOTE) else result
    }

    private suspend fun perform(service: AccessibilityService, action: PhoneAction, say: String?): ActionResult {
        return when (action) {
            is PhoneAction.Tap -> tap(service, action.target, say, Press.TAP)
            is PhoneAction.LongPress -> tap(service, action.target, say, Press.LONG)
            is PhoneAction.DoubleTap -> tap(service, action.target, say, Press.DOUBLE)
            is PhoneAction.TapAt -> tapPoint(service, action.x, action.y, say)
            is PhoneAction.Type -> type(service, action, say)
            is PhoneAction.Scroll -> scroll(service, action, say)
            is PhoneAction.ScrollTo -> scrollTo(service, action, say)
            is PhoneAction.Swipe -> swipe(service, action.spec, say)
            is PhoneAction.Drag -> drag(service, action, say)
            is PhoneAction.Press -> press(service, action.key, say)
            is PhoneAction.OpenApp -> openApp(service, action.name, say)
            is PhoneAction.Wait -> pause(action.millis, say)
            is PhoneAction.WaitFor -> waitFor(service, action, say)
        }
    }

    private enum class Press { TAP, LONG, DOUBLE }

    private sealed interface Located {
        class Ready(val element: ScreenElement, val node: AccessibilityNodeInfo?, val box: ScreenBox) : Located
        class Problem(val message: String) : Located
    }

    private fun locate(service: AccessibilityService, target: Target): Located = when (target) {
        is Target.Numbered -> {
            val snapshot = reader.last
            val element = snapshot?.elements?.getOrNull(target.number - 1)
            when {
                snapshot == null -> Located.Problem("Read the screen first with phone_screen")
                element == null -> Located.Problem("No element [${target.number}] on the last screen, read it again")
                else -> ready(service, snapshot, element)
            }
        }
        is Target.Selected -> {
            val snapshot = reader.snapshot(service)
            when (val pick = ElementPicker.pick(target.selector, snapshot.elements)) {
                is Pick.Found -> ready(service, snapshot, pick.element)
                is Pick.Ambiguous -> Located.Problem(ambiguity(target.selector, pick.candidates))
                is Pick.Missing -> Located.Problem(missing(target.selector, pick.matches, snapshot))
            }
        }
    }

    private fun ready(service: AccessibilityService, snapshot: ScreenSnapshot, element: ScreenElement): Located.Ready {
        val node = snapshot.nodes[element.number]
        val screen = reader.screenSize(service)
        val fresh = node?.refresh() == true
        val live = if (fresh) Rect().also { node.getBoundsInScreen(it) }.takeUnless { it.isEmpty }?.let(ScreenBox::of) else null
        val box = (live ?: element.bounds).clippedTo(screen)
        return Located.Ready(element, node, box)
    }

    private fun ScreenBox.clippedTo(screen: Point): ScreenBox {
        val clipped = ScreenBox(left.coerceAtLeast(0), top.coerceAtLeast(0), right.coerceAtMost(screen.x), bottom.coerceAtMost(screen.y))
        return if (clipped.isEmpty) this else clipped
    }

    private fun ambiguity(selector: ElementSelector, candidates: List<ScreenElement>): String =
        "Several elements match ${selector.describe()}, nothing was done. Add index (counting from 1) or a more specific target:\n" +
            candidates.joinToString("\n") { ScreenText.line(it, detailed = false) }

    private fun missing(selector: ElementSelector, matches: Int, snapshot: ScreenSnapshot): String {
        val prefix = if (selector.index != null && matches > 0) "Only $matches elements match ${selector.describe().substringBefore(" #")}, there is no #${selector.index}"
        else "No element matches ${selector.describe()}"
        val scrollable = snapshot.elements.any { it.scrollable }
        val advice = if (scrollable) "Try scroll_to, or read the screen to see what is there." else "Read the screen to see what is there."
        return "$prefix (${snapshot.elements.size + snapshot.hidden} elements on the screen). $advice"
    }

    private fun label(element: ScreenElement): String = element.label.ifEmpty { element.role }.take(CAPTION_LABEL_CHARS)

    private fun name(element: ScreenElement): String = "[${element.number}] ${label(element)}"

    private suspend fun tap(service: AccessibilityService, target: Target, say: String?, kind: Press): ActionResult {
        val located = locate(service, target)
        if (located !is Located.Ready) return ActionResult.Failed((located as Located.Problem).message)
        val x = located.box.centerX
        val y = located.box.centerY
        val (automatic, verb, noun) = when (kind) {
            Press.TAP -> Triple(R.string.phone_use_tap, "Tapped", "tap")
            Press.LONG -> Triple(R.string.phone_use_long_press, "Long-pressed", "long press")
            Press.DOUBLE -> Triple(R.string.phone_use_double_tap, "Double-tapped", "double tap")
        }
        val caption = caption(say, automatic, label(located.element))
        val perform: suspend () -> ActionResult = {
            if (pressElement(service, located, kind)) ActionResult.Done("$verb ${name(located.element)}")
            else ActionResult.Failed("The $noun on ${name(located.element)} did not go through, the system cancelled the gesture")
        }
        return if (kind == Press.DOUBLE) session.doubleClick(x, y, caption, perform) else session.click(x, y, caption, perform)
    }

    private suspend fun tapPoint(service: AccessibilityService, x: Int, y: Int, say: String?): ActionResult =
        session.click(x, y, caption(say, R.string.phone_use_tap_point)) {
            if (retrying { gestures.tap(service, x, y) } || injector.tap(x, y)) ActionResult.Done("Tapped at $x,$y") else ActionResult.Failed("The system cancelled the gesture")
        }

    private suspend fun pressElement(service: AccessibilityService, target: Located.Ready, kind: Press): Boolean {
        val x = target.box.centerX
        val y = target.box.centerY
        val gesture: suspend () -> Boolean = when (kind) {
            Press.TAP -> { { gestures.tap(service, x, y) } }
            Press.LONG -> { { gestures.longPress(service, x, y) } }
            Press.DOUBLE -> { { gestures.doubleTap(service, x, y) } }
        }
        if (retrying(gesture)) return true
        val semantic = target.node?.let(::clickableAncestor)
        val viaNode = when (kind) {
            Press.TAP -> semantic?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Press.LONG -> semantic?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            Press.DOUBLE -> semantic?.let { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        }
        if (viaNode == true) return true
        return when (kind) {
            Press.TAP -> injector.tap(x, y)
            Press.LONG -> injector.longPress(x, y, PhoneGestures.LONG_PRESS_MS)
            Press.DOUBLE -> injector.tap(x, y) && injector.tap(x, y)
        }
    }

    private suspend fun retrying(gesture: suspend () -> Boolean): Boolean {
        repeat(GESTURE_ATTEMPTS) { attempt ->
            if (gesture()) return true
            if (attempt < GESTURE_ATTEMPTS - 1) delay(GESTURE_RETRY_MS)
        }
        return false
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        generateSequence(node) { it.parent }.firstOrNull { it.isClickable || it.isLongClickable }

    private suspend fun type(service: AccessibilityService, action: PhoneAction.Type, say: String?): ActionResult {
        val caption = caption(say, R.string.phone_use_type, action.text.take(CAPTION_LABEL_CHARS))
        val field = action.target?.let { target ->
            val located = locate(service, target)
            if (located !is Located.Ready) return ActionResult.Failed((located as Located.Problem).message)
            located
        }
        var input = reader.frontRoot(service)?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (field != null && !isSameField(input, field)) {
            field.node?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            input = reader.frontRoot(service)?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (!isSameField(input, field)) {
                session.click(field.box.centerX, field.box.centerY, caption) { pressElement(service, field, Press.TAP) }
                delay(FOCUS_SETTLE_MS)
                input = reader.frontRoot(service)?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: field.node
            }
        }
        val editable = input ?: return ActionResult.Failed("No input field is focused, tap one first or pass its element number or target")
        if (!editable.isEditable) return ActionResult.Failed("That element is not a text field")
        val bounds = Rect().also(editable::getBoundsInScreen)
        if (field == null) session.aim(bounds.centerX(), bounds.centerY(), caption)
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text) }
        if (!editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            delay(GESTURE_RETRY_MS)
            if (!editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return ActionResult.Failed("The field refused the text")
        }
        if (!action.submit) return ActionResult.Done("Typed the text")
        return ActionResult.Done("Typed the text and " + submit(service, editable, ScreenBox.of(bounds), caption))
    }

    private fun isSameField(focused: AccessibilityNodeInfo?, field: Located.Ready): Boolean {
        if (focused == null) return false
        if (field.node != null && focused == field.node) return true
        val bounds = Rect().also(focused::getBoundsInScreen)
        return ScreenBox.of(bounds).intersects(field.box) && bounds.contains(field.box.centerX, field.box.centerY)
    }

    private suspend fun submit(service: AccessibilityService, field: AccessibilityNodeInfo, box: ScreenBox, caption: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && field.performAction(AccessibilityAction.ACTION_IME_ENTER.id)) return "pressed enter"
        if (injector.key(InputInjector.KEYCODE_ENTER)) return "pressed enter through the system shell"
        val snapshot = reader.snapshot(service)
        val button = submitButton(snapshot, box)
        if (button != null) {
            val located = ready(service, snapshot, button)
            val tapped = session.click(located.box.centerX, located.box.centerY, caption) { pressElement(service, located, Press.TAP) }
            if (tapped) return "tapped the ${name(button)} button because enter is not supported here"
        }
        return "could not submit: enter is not supported in this field, find and tap the send or search button"
    }

    private fun submitButton(snapshot: ScreenSnapshot, field: ScreenBox): ScreenElement? =
        snapshot.elements
            .filter { it.clickable && SUBMIT_LABEL.containsMatchIn(it.label) }
            .minByOrNull { distance(it.bounds, field) }

    private fun distance(first: ScreenBox, second: ScreenBox): Int =
        kotlin.math.abs(first.centerX - second.centerX) + kotlin.math.abs(first.centerY - second.centerY)

    private suspend fun scroll(service: AccessibilityService, action: PhoneAction.Scroll, say: String?): ActionResult {
        val container = when (val target = action.target) {
            null -> reader.snapshot(service).let { snapshot -> snapshot.elements.filter { it.scrollable }.maxByOrNull { it.bounds.width * it.bounds.height }?.let { ready(service, snapshot, it) } }
            else -> locate(service, target).let { located ->
                if (located !is Located.Ready) return ActionResult.Failed((located as Located.Problem).message)
                located
            }
        }
        val caption = caption(say, R.string.phone_use_scroll, directionLabel(action.direction))
        val screen = reader.screenSize(service)
        val box = container?.box ?: ScreenBox(0, 0, screen.x, screen.y)
        session.aim(box.centerX, box.centerY, caption)
        if (container?.node?.let { scrollNode(it, action.direction) } == true) return ActionResult.Done("Scrolled ${action.direction.key}")
        val plan = SwipePlanner.inside(box, action.direction.opposite)
        return session.dragging(plan.from.x, plan.from.y, plan.to.x, plan.to.y, plan.durationMs, caption) {
            if (swipeWithFallbacks(service, plan)) ActionResult.Done("Scrolled ${action.direction.key} with a swipe") else ActionResult.Failed("The system cancelled the gesture")
        }
    }

    private fun scrollNode(node: AccessibilityNodeInfo, direction: SwipeDirection): Boolean {
        val (exact, fallback) = when (direction) {
            SwipeDirection.UP -> AccessibilityAction.ACTION_SCROLL_UP to AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            SwipeDirection.DOWN -> AccessibilityAction.ACTION_SCROLL_DOWN to AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            SwipeDirection.LEFT -> AccessibilityAction.ACTION_SCROLL_LEFT to AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            SwipeDirection.RIGHT -> AccessibilityAction.ACTION_SCROLL_RIGHT to AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> return false
        }
        return node.performAction(exact.id) || node.performAction(fallback)
    }

    private suspend fun scrollTo(service: AccessibilityService, action: PhoneAction.ScrollTo, say: String?): ActionResult {
        val caption = caption(say, R.string.phone_use_scroll_to, action.selector.text ?: action.selector.describe())
        session.say(caption)
        val directions = action.direction?.let { listOf(it) } ?: listOf(SwipeDirection.DOWN, SwipeDirection.UP)
        val screen = reader.screenSize(service)
        var container = ScreenBox(0, 0, screen.x, screen.y)
        val search = ScrollSearch(
            isVisible = {
                var snapshot = reader.snapshot(service)
                container = snapshot.elements.filter { it.scrollable }.maxByOrNull { it.bounds.width * it.bounds.height }?.bounds ?: container
                var pick = ElementPicker.pick(action.selector, snapshot.elements)
                val revealing = when (pick) {
                    is Pick.Missing -> showOffScreen(service, action.selector)
                    is Pick.Found -> pick.element.partial && revealed(snapshot, pick.element)
                    is Pick.Ambiguous -> false
                }
                if (revealing) {
                    settle(service, SETTLE_SCROLL_MS)
                    snapshot = reader.snapshot(service)
                    pick = ElementPicker.pick(action.selector, snapshot.elements)
                }
                pick is Pick.Ambiguous || (pick is Pick.Found && !pick.element.partial)
            },
            fingerprint = { reader.probe(service).fingerprint },
            scrollOnce = { direction ->
                val plan = SwipePlanner.inside(container, direction.opposite, durationMs = SCROLL_STEP_MS)
                val done = session.dragging(plan.from.x, plan.from.y, plan.to.x, plan.to.y, plan.durationMs, caption) {
                    gestures.swipeAndHold(service, plan.from, plan.to, plan.durationMs) || swipeWithFallbacks(service, plan)
                }
                if (done) settle(service, SETTLE_SCROLL_MS)
                done
            },
        )
        val outcome = search.run(directions, action.maxSwipes)
        val described = action.selector.describe()
        if (outcome !is SearchOutcome.Found) {
            return ActionResult.Failed(
                when (outcome) {
                    is SearchOutcome.EndReached -> "Scrolled to the end of the list in ${outcome.swipes} swipes and did not find $described"
                    is SearchOutcome.LimitReached -> "Did not find $described in ${outcome.swipes} swipes, raise max_swipes or look at the screen"
                    is SearchOutcome.GestureFailed -> "The system cancelled the scroll gesture after ${outcome.swipes} swipes"
                    is SearchOutcome.Found -> error("unreachable")
                },
            )
        }
        val snapshot = reader.snapshot(service)
        return when (val pick = ElementPicker.pick(action.selector, snapshot.elements)) {
            is Pick.Ambiguous -> ActionResult.Failed(ambiguity(action.selector, pick.candidates))
            is Pick.Missing -> ActionResult.Failed("$described scrolled out of view again, try once more")
            is Pick.Found -> {
                centerOnScreen(service, container, pick.element)
                val final = reader.snapshot(service).let { ElementPicker.pick(action.selector, it.elements) as? Pick.Found }?.element ?: pick.element
                ActionResult.Done("Found ${name(final)} after ${outcome.swipes} swipes, it is at ${final.bounds.centerX},${final.bounds.centerY}")
            }
        }
    }

    private fun revealed(snapshot: ScreenSnapshot, element: ScreenElement): Boolean {
        val node = snapshot.nodes[element.number] ?: return false
        return node.performAction(AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
    }

    private fun showOffScreen(service: AccessibilityService, selector: ElementSelector): Boolean {
        val text = selector.text ?: return false
        val node = ScreenTree.findAnywhere(reader.frontRoot(service), text).firstOrNull { !it.isVisibleToUser } ?: return false
        return node.performAction(AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
    }

    private suspend fun centerOnScreen(service: AccessibilityService, container: ScreenBox, element: ScreenElement) {
        val offset = element.bounds.centerY - container.centerY
        if (kotlin.math.abs(offset) < container.height * CENTER_TOLERANCE) return
        val finger = if (offset > 0) SwipeDirection.UP else SwipeDirection.DOWN
        val reach = (kotlin.math.abs(offset).toFloat() * 2f / container.height).coerceIn(MIN_CENTER_REACH, SwipePlanner.CONTAINER_REACH)
        val plan = SwipePlanner.inside(container, finger, reach, SCROLL_STEP_MS)
        session.dragging(plan.from.x, plan.from.y, plan.to.x, plan.to.y, plan.durationMs, null) {
            gestures.swipeAndHold(service, plan.from, plan.to, plan.durationMs)
        }
        settle(service, SETTLE_SCROLL_MS)
    }

    private suspend fun swipe(service: AccessibilityService, spec: SwipeSpec, say: String?): ActionResult {
        val screen = reader.screenSize(service)
        val plan = SwipePlanner.plan(spec, screen.x, screen.y)
        val label = spec.direction?.takeUnless { it.isDiagonal }?.let { directionLabel(it) }
        val caption = if (label != null) caption(say, R.string.phone_use_swipe, label) else caption(say, R.string.phone_use_swipe_any)
        return session.dragging(plan.from.x, plan.from.y, plan.to.x, plan.to.y, plan.durationMs, caption) {
            if (swipeWithFallbacks(service, plan)) ActionResult.Done("Swiped from ${plan.from.x},${plan.from.y} to ${plan.to.x},${plan.to.y}") else ActionResult.Failed("The system cancelled the swipe gesture")
        }
    }

    private suspend fun swipeWithFallbacks(service: AccessibilityService, plan: SwipePlan): Boolean =
        retrying { gestures.swipe(service, plan.from, plan.to, plan.durationMs) } || injector.swipe(plan.from, plan.to, plan.durationMs)

    private suspend fun drag(service: AccessibilityService, action: PhoneAction.Drag, say: String?): ActionResult {
        val screen = reader.screenSize(service)
        val points = action.path.map { ScreenPoint(it.x.coerceIn(0, screen.x - 1), it.y.coerceIn(0, screen.y - 1)) }
        val duration = action.durationMs ?: (DRAG_BASE_MS + DRAG_PER_POINT_MS * points.size)
        val first = points.first()
        val last = points.last()
        return session.dragging(first.x, first.y, last.x, last.y, duration, caption(say, R.string.phone_use_drag)) {
            if (retrying { gestures.path(service, points, duration) }) ActionResult.Done("Dragged through ${points.size} points") else ActionResult.Failed("The system cancelled the drag gesture")
        }
    }

    private fun press(service: AccessibilityService, key: GlobalKey, say: String?): ActionResult {
        session.say(caption(say, R.string.phone_use_press, keyLabel(key)))
        val code = when (key) {
            GlobalKey.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalKey.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalKey.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalKey.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            GlobalKey.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            GlobalKey.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            GlobalKey.SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
        }
        return if (service.performGlobalAction(code)) ActionResult.Done("Pressed ${key.key}") else ActionResult.Failed("The system refused ${key.key}")
    }

    private suspend fun openApp(service: AccessibilityService, name: String, say: String?): ActionResult {
        val app = InstalledApps.find(service.packageManager, name) ?: return ActionResult.Failed("No installed app matches \"$name\"")
        session.say(caption(say, R.string.phone_use_open, app.label))
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val started = runCatching { service.startActivity(intent) }
        if (started.isFailure) return ActionResult.Failed("Could not open ${app.label}: ${started.exceptionOrNull()?.message}")
        val arrived = waiter(service).await(WaitCondition.AppInFront(app.packageName), OPEN_APP_WAIT_MS)
        return ActionResult.Done(if (arrived.met) "Opened ${app.label}" else "Started ${app.label}, it may still be loading")
    }

    private suspend fun pause(millis: Long, say: String?): ActionResult {
        session.say(caption(say, R.string.phone_use_wait))
        session.waiting(true)
        try {
            delay(millis)
        } finally {
            session.waiting(false)
        }
        return ActionResult.Done("Waited $millis ms")
    }

    private suspend fun waitFor(service: AccessibilityService, action: PhoneAction.WaitFor, say: String?): ActionResult {
        val condition = when (val wanted = action.condition) {
            is WaitCondition.AppInFront -> WaitCondition.AppInFront(InstalledApps.find(service.packageManager, wanted.packageName)?.packageName ?: wanted.packageName)
            else -> wanted
        }
        session.say(caption(say, R.string.phone_use_wait_for, waitSubject(condition)))
        session.waiting(true)
        try {
            val outcome = waiter(service).await(condition, action.timeoutMs)
            return if (outcome.met) ActionResult.Done(outcome.detail) else ActionResult.Failed(outcome.detail)
        } finally {
            session.waiting(false)
        }
    }

    private fun waitSubject(condition: WaitCondition): String = when (condition) {
        is WaitCondition.TextVisible -> condition.text
        is WaitCondition.TextGone -> condition.text
        is WaitCondition.AppInFront -> condition.packageName
        is WaitCondition.Idle -> context.getString(R.string.phone_use_wait_idle)
        WaitCondition.ScreenChanged -> context.getString(R.string.phone_use_wait_change)
    }

    private fun waiter(service: AccessibilityService) = Waiter(probe = { reader.probe(service) }, clock = SystemClock::elapsedRealtime)

    private suspend fun settle(service: AccessibilityService, maxMs: Long) {
        delay(SETTLE_FIRST_MS)
        var waited = SETTLE_FIRST_MS
        var previous = reader.probe(service).fingerprint
        while (waited < maxMs) {
            delay(SETTLE_POLL_MS)
            waited += SETTLE_POLL_MS
            val current = reader.probe(service).fingerprint
            if (current == previous) return
            previous = current
        }
    }

    private suspend fun leaveOwnApp(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        var waited = 0L
        while (waited < LEAVE_APP_WAIT_MS && reader.isOwnAppInFront(service)) {
            delay(SETTLE_POLL_MS)
            waited += SETTLE_POLL_MS
        }
        settle(service, SETTLE_BETWEEN_MS)
    }

    private fun caption(say: String?, @StringRes automatic: Int, vararg arguments: Any): String = say ?: context.getString(automatic, *arguments)

    private fun directionLabel(direction: SwipeDirection): String = context.getString(
        when (direction) {
            SwipeDirection.UP -> R.string.phone_use_up
            SwipeDirection.DOWN -> R.string.phone_use_down
            SwipeDirection.LEFT -> R.string.phone_use_left
            SwipeDirection.RIGHT -> R.string.phone_use_right
            else -> R.string.phone_use_swipe_any
        },
    )

    private fun keyLabel(key: GlobalKey): String = context.getString(
        when (key) {
            GlobalKey.BACK -> R.string.phone_use_key_back
            GlobalKey.HOME -> R.string.phone_use_key_home
            GlobalKey.RECENTS -> R.string.phone_use_key_recents
            GlobalKey.NOTIFICATIONS -> R.string.phone_use_key_notifications
            GlobalKey.QUICK_SETTINGS -> R.string.phone_use_key_quick_settings
            GlobalKey.LOCK_SCREEN -> R.string.phone_use_key_lock
            GlobalKey.SCREENSHOT -> R.string.phone_use_key_screenshot
        },
    )

    private fun ActionResult.withNote(note: String): ActionResult = when (this) {
        is ActionResult.Done -> ActionResult.Done("$note ${this.note}")
        is ActionResult.Failed -> ActionResult.Failed("$note $reason")
    }

    companion object {
        private const val HOME_FIRST_NOTE = "(The assistant's own app was in front, so I went to the home screen first.)"
        private const val LOOP_HINT = "Hint: the same action has changed nothing three times in a row. Try another element or approach, scroll, or look at the pixels with phone_screenshot."
        private const val CAPTION_LABEL_CHARS = 40
        private const val GESTURE_ATTEMPTS = 2
        private const val GESTURE_RETRY_MS = 150L
        private const val FOCUS_SETTLE_MS = 200L
        private const val SETTLE_FIRST_MS = 120L
        private const val SETTLE_POLL_MS = 100L
        private const val SETTLE_MAX_MS = 700L
        private const val SETTLE_BETWEEN_MS = 500L
        private const val SETTLE_SCROLL_MS = 450L
        private const val LEAVE_APP_WAIT_MS = 1_500L
        private const val OPEN_APP_WAIT_MS = 3_000L
        private const val SCROLL_STEP_MS = 420L
        private const val CENTER_TOLERANCE = 0.3f
        private const val MIN_CENTER_REACH = 0.15f
        private const val DRAG_BASE_MS = 300L
        private const val DRAG_PER_POINT_MS = 120L
        private val SUBMIT_LABEL = Regex("""(?i)\b(search|send|go|done|submit|enter|ok)\b|найти|поиск|искать|отправить|готово|перейти|далее""")
    }
}

internal fun PhoneAction.touchesScreen(): Boolean = when (this) {
    is PhoneAction.Press, is PhoneAction.OpenApp, is PhoneAction.Wait, is PhoneAction.WaitFor -> false
    else -> true
}

internal fun PhoneAction.mayChangeScreen(): Boolean = when (this) {
    is PhoneAction.Wait, is PhoneAction.WaitFor -> false
    else -> true
}

internal fun PhoneAction.usesNumbers(): Boolean = when (this) {
    is PhoneAction.Tap -> target is Target.Numbered
    is PhoneAction.LongPress -> target is Target.Numbered
    is PhoneAction.DoubleTap -> target is Target.Numbered
    is PhoneAction.Type -> target is Target.Numbered
    is PhoneAction.Scroll -> target is Target.Numbered
    else -> false
}
