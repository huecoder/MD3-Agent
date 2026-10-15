package dev.huecoder.md3agent.system.control

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import dev.huecoder.md3agent.R
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneControl(private val context: Context) {
    private val service = MutableStateFlow<AccessibilityService?>(null)
    private val gestures = PhoneGestures()
    private val screenshots = PhoneScreenshots()
    val session = PhoneUseSession()
    @Volatile
    private var lastSnapshot: ScreenSnapshot? = null

    val connected: StateFlow<AccessibilityService?> = service.asStateFlow()

    fun attach(connected: AccessibilityService) {
        service.value = connected
    }

    fun detach(disconnected: AccessibilityService) {
        service.compareAndSet(disconnected, null)
    }

    fun isEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = ComponentName(context, PhoneControlService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun isReady(): Boolean = service.value != null

    fun screen(say: String? = null): ScreenSnapshot? {
        val active = service.value ?: return null
        session.say(caption(say, R.string.phone_use_looking))
        return snapshot(active)
    }

    private fun snapshot(active: AccessibilityService): ScreenSnapshot {
        val size = screenSize(active)
        return ScreenTree.capture(frontRoot(active), size.x, size.y).also { lastSnapshot = it }
    }

    suspend fun screenshot(region: ScreenRegion, marks: Boolean, say: String? = null): ScreenshotResult {
        val active = service.value ?: return ScreenshotResult.Failed(NOT_CONNECTED)
        session.say(caption(say, R.string.phone_use_screenshot))
        val numbered = if (marks) snapshot(active).elements else emptyList()
        val area = when (region) {
            ScreenRegion.Full -> null
            is ScreenRegion.Area -> Rect(region.x, region.y, region.x + region.width, region.y + region.height)
            is ScreenRegion.Element -> {
                val element = lastSnapshot?.elements?.getOrNull(region.number - 1)
                    ?: return ScreenshotResult.Failed("No element [${region.number}] on the last screen, read it again with phone_screen")
                Rect(element.bounds).apply { inset(-ELEMENT_CROP_PADDING_PX, -ELEMENT_CROP_PADDING_PX) }
            }
        }
        return session.withOverlayHidden { screenshots.capture(active, area, numbered) }
    }

    suspend fun perform(action: PhoneAction, say: String? = null): ActionResult {
        val active = service.value ?: return ActionResult.Failed(NOT_CONNECTED)
        if (action.touchesScreen() && frontRoot(active)?.packageName == context.packageName) {
            return ActionResult.Failed("The assistant's own app is in front. Leave it with open_app or home before touching the screen.")
        }
        return when (action) {
            is PhoneAction.Tap -> withElement(action.element) { node, bounds, label ->
                session.click(bounds.centerX(), bounds.centerY(), caption(say, R.string.phone_use_tap, label)) {
                    if (clickable(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) done("Tapped [${action.element}]")
                    else gestureResult(gestures.tap(active, bounds.centerX(), bounds.centerY()), "Tapped [${action.element}]")
                }
            }
            is PhoneAction.LongPress -> withElement(action.element) { node, bounds, label ->
                session.click(bounds.centerX(), bounds.centerY(), caption(say, R.string.phone_use_long_press, label)) {
                    if (clickable(node)?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true) done("Long-pressed [${action.element}]")
                    else gestureResult(gestures.longPress(active, bounds.centerX(), bounds.centerY()), "Long-pressed [${action.element}]")
                }
            }
            is PhoneAction.TapAt -> session.click(action.x, action.y, caption(say, R.string.phone_use_tap_point)) {
                gestureResult(gestures.tap(active, action.x, action.y), "Tapped at ${action.x},${action.y}")
            }
            is PhoneAction.Type -> type(active, action, say)
            is PhoneAction.Scroll -> scroll(active, action, say)
            is PhoneAction.Swipe -> swipe(active, action.direction, caption(say, R.string.phone_use_swipe, directionLabel(action.direction)), "Swiped ${action.direction.key}")
            is PhoneAction.Press -> press(active, action.key, say)
            is PhoneAction.OpenApp -> openApp(active, action.name, say)
            is PhoneAction.Wait -> {
                session.say(caption(say, R.string.phone_use_wait))
                session.waiting(true)
                try {
                    delay(action.millis)
                } finally {
                    session.waiting(false)
                }
                done("Waited ${action.millis} ms")
            }
        }
    }

    private suspend fun swipe(active: AccessibilityService, direction: SwipeDirection, caption: String, note: String): ActionResult {
        val screen = screenSize(active)
        val path = gestures.swipePoints(direction, screen)
        return session.dragging(path.fromX, path.fromY, path.toX, path.toY, PhoneGestures.SWIPE_MS, caption) {
            gestureResult(gestures.swipe(active, direction, screen), note)
        }
    }

    private fun caption(say: String?, @StringRes automatic: Int, vararg arguments: Any): String =
        say ?: context.getString(automatic, *arguments)

    private fun directionLabel(direction: SwipeDirection): String = context.getString(
        when (direction) {
            SwipeDirection.UP -> R.string.phone_use_up
            SwipeDirection.DOWN -> R.string.phone_use_down
            SwipeDirection.LEFT -> R.string.phone_use_left
            SwipeDirection.RIGHT -> R.string.phone_use_right
        },
    )

    private suspend fun withElement(number: Int, act: suspend (AccessibilityNodeInfo, Rect, String) -> ActionResult): ActionResult {
        val snapshot = lastSnapshot ?: return ActionResult.Failed("Read the screen first with phone_screen")
        val node = snapshot.nodes[number] ?: return ActionResult.Failed("No element [$number] on the last screen, read it again")
        val element = snapshot.elements[number - 1]
        val fresh = node.refresh()
        val bounds = if (fresh) Rect().also(node::getBoundsInScreen).takeUnless { it.isEmpty } ?: element.bounds else element.bounds
        return act(node, bounds, element.label.ifEmpty { element.role }.take(CAPTION_LABEL_CHARS))
    }

    private fun frontRoot(active: AccessibilityService): AccessibilityNodeInfo? {
        val focused = active.rootInActiveWindow
        if (focused?.window?.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return focused
        val below = active.windows.filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
        return (below.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } ?: below.firstOrNull())?.root
    }

    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        generateSequence(node) { it.parent }.firstOrNull { it.isClickable || it.isLongClickable }

    private suspend fun type(active: AccessibilityService, action: PhoneAction.Type, say: String?): ActionResult {
        val target = action.element?.let { lastSnapshot?.nodes?.get(it)?.apply { refresh() } }
            ?: frontRoot(active)?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return ActionResult.Failed("No input field is focused, tap one first or pass its element number")
        if (!target.isEditable) return ActionResult.Failed("That element is not a text field")
        val field = Rect().also(target::getBoundsInScreen)
        session.aim(field.centerX(), field.centerY(), caption(say, R.string.phone_use_type, action.text.take(CAPTION_LABEL_CHARS)))
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text) }
        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return ActionResult.Failed("The field refused the text")
        if (!action.submit) return done("Typed the text")
        val submitted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            target.performAction(AccessibilityAction.ACTION_IME_ENTER.id)
        return done(if (submitted) "Typed the text and pressed enter" else "Typed the text, but enter is not supported here, tap the send button")
    }

    private suspend fun scroll(active: AccessibilityService, action: PhoneAction.Scroll, say: String?): ActionResult {
        val node = action.element?.let { lastSnapshot?.nodes?.get(it)?.apply { refresh() } }
            ?: lastSnapshot?.let { snapshot -> snapshot.elements.firstOrNull { it.scrollable }?.let { snapshot.nodes[it.number] } }
        val caption = caption(say, R.string.phone_use_scroll, directionLabel(action.direction))
        node?.let { Rect().also(it::getBoundsInScreen) }?.let { session.aim(it.centerX(), it.centerY(), caption) }
        val scrolled = node?.let { target ->
            val (exact, fallback) = when (action.direction) {
                SwipeDirection.UP -> AccessibilityAction.ACTION_SCROLL_UP to AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                SwipeDirection.DOWN -> AccessibilityAction.ACTION_SCROLL_DOWN to AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                SwipeDirection.LEFT -> AccessibilityAction.ACTION_SCROLL_LEFT to AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                SwipeDirection.RIGHT -> AccessibilityAction.ACTION_SCROLL_RIGHT to AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            target.performAction(exact.id) || target.performAction(fallback)
        } == true
        if (scrolled) return done("Scrolled ${action.direction.key}")
        val finger = when (action.direction) {
            SwipeDirection.UP -> SwipeDirection.DOWN
            SwipeDirection.DOWN -> SwipeDirection.UP
            SwipeDirection.LEFT -> SwipeDirection.RIGHT
            SwipeDirection.RIGHT -> SwipeDirection.LEFT
        }
        return swipe(active, finger, caption, "Scrolled ${action.direction.key} with a swipe")
    }

    private fun press(active: AccessibilityService, key: GlobalKey, say: String?): ActionResult {
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
        return if (active.performGlobalAction(code)) done("Pressed ${key.key}") else ActionResult.Failed("The system refused ${key.key}")
    }

    private fun openApp(active: AccessibilityService, name: String, say: String?): ActionResult {
        val app = InstalledApps.find(active.packageManager, name)
            ?: return ActionResult.Failed("No installed app matches \"$name\"")
        session.say(caption(say, R.string.phone_use_open, app.label))
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { active.startActivity(intent) }
            .fold({ done("Opened ${app.label}") }, { ActionResult.Failed("Could not open ${app.label}: ${it.message}") })
    }

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

    private fun gestureResult(completed: Boolean, note: String) =
        if (completed) done(note) else ActionResult.Failed("The system cancelled the gesture")

    private fun done(note: String) = ActionResult.Done(note)

    private fun PhoneAction.touchesScreen() = when (this) {
        is PhoneAction.Tap, is PhoneAction.LongPress, is PhoneAction.TapAt, is PhoneAction.Type, is PhoneAction.Scroll, is PhoneAction.Swipe -> true
        is PhoneAction.Press, is PhoneAction.OpenApp, is PhoneAction.Wait -> false
    }

    private fun screenSize(active: AccessibilityService): Point = active.getSystemService(WindowManager::class.java).realDisplaySize()

    companion object {
        private const val ELEMENT_CROP_PADDING_PX = 16
        private const val CAPTION_LABEL_CHARS = 40
        const val NOT_CONNECTED = "Phone control is off. Ask the user to enable MD3 Agent in Settings > Accessibility."
    }
}
