package dev.huecoder.md3agent.system.control

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneControl(private val context: Context) {
    private val service = MutableStateFlow<AccessibilityService?>(null)
    private val gestures = PhoneGestures()
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

    fun screen(): ScreenSnapshot? {
        val active = service.value ?: return null
        val size = screenSize(active)
        return ScreenTree.capture(active.rootInActiveWindow, size.x, size.y).also { lastSnapshot = it }
    }

    suspend fun perform(action: PhoneAction): ActionResult {
        val active = service.value ?: return ActionResult.Failed(NOT_CONNECTED)
        if (action.touchesScreen() && active.rootInActiveWindow?.packageName == context.packageName) {
            return ActionResult.Failed("The assistant's own app is in front. Leave it with open_app or home before touching the screen.")
        }
        return when (action) {
            is PhoneAction.Tap -> withElement(action.element) { node, bounds ->
                if (clickable(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) done("Tapped [${action.element}]")
                else gestureResult(gestures.tap(active, bounds.centerX(), bounds.centerY()), "Tapped [${action.element}]")
            }
            is PhoneAction.LongPress -> withElement(action.element) { node, bounds ->
                if (clickable(node)?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true) done("Long-pressed [${action.element}]")
                else gestureResult(gestures.longPress(active, bounds.centerX(), bounds.centerY()), "Long-pressed [${action.element}]")
            }
            is PhoneAction.TapAt -> gestureResult(gestures.tap(active, action.x, action.y), "Tapped at ${action.x},${action.y}")
            is PhoneAction.Type -> type(active, action)
            is PhoneAction.Scroll -> scroll(active, action)
            is PhoneAction.Swipe -> gestureResult(gestures.swipe(active, action.direction, screenSize(active)), "Swiped ${action.direction.key}")
            is PhoneAction.Press -> press(active, action.key)
            is PhoneAction.OpenApp -> openApp(active, action.name)
            is PhoneAction.Wait -> {
                delay(action.millis)
                done("Waited ${action.millis} ms")
            }
        }
    }

    private inline fun withElement(number: Int, act: (AccessibilityNodeInfo, Rect) -> ActionResult): ActionResult {
        val snapshot = lastSnapshot ?: return ActionResult.Failed("Read the screen first with phone_screen")
        val node = snapshot.nodes[number] ?: return ActionResult.Failed("No element [$number] on the last screen, read it again")
        val bounds = snapshot.elements[number - 1].bounds
        node.refresh()
        return act(node, bounds)
    }

    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        generateSequence(node) { it.parent }.firstOrNull { it.isClickable || it.isLongClickable }

    private fun type(active: AccessibilityService, action: PhoneAction.Type): ActionResult {
        val target = action.element?.let { lastSnapshot?.nodes?.get(it)?.apply { refresh() } }
            ?: active.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return ActionResult.Failed("No input field is focused, tap one first or pass its element number")
        if (!target.isEditable) return ActionResult.Failed("That element is not a text field")
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text) }
        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return ActionResult.Failed("The field refused the text")
        if (!action.submit) return done("Typed the text")
        val submitted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            target.performAction(AccessibilityAction.ACTION_IME_ENTER.id)
        return done(if (submitted) "Typed the text and pressed enter" else "Typed the text, but enter is not supported here, tap the send button")
    }

    private suspend fun scroll(active: AccessibilityService, action: PhoneAction.Scroll): ActionResult {
        val node = action.element?.let { lastSnapshot?.nodes?.get(it)?.apply { refresh() } }
            ?: lastSnapshot?.let { snapshot -> snapshot.elements.firstOrNull { it.scrollable }?.let { snapshot.nodes[it.number] } }
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
        return gestureResult(gestures.swipe(active, finger, screenSize(active)), "Scrolled ${action.direction.key} with a swipe")
    }

    private fun press(active: AccessibilityService, key: GlobalKey): ActionResult {
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

    private fun openApp(active: AccessibilityService, name: String): ActionResult {
        val app = InstalledApps.find(active.packageManager, name)
            ?: return ActionResult.Failed("No installed app matches \"$name\"")
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { active.startActivity(intent) }
            .fold({ done("Opened ${app.label}") }, { ActionResult.Failed("Could not open ${app.label}: ${it.message}") })
    }

    private fun gestureResult(completed: Boolean, note: String) =
        if (completed) done(note) else ActionResult.Failed("The system cancelled the gesture")

    private fun done(note: String) = ActionResult.Done(note)

    private fun PhoneAction.touchesScreen() = when (this) {
        is PhoneAction.Tap, is PhoneAction.LongPress, is PhoneAction.TapAt, is PhoneAction.Type, is PhoneAction.Scroll, is PhoneAction.Swipe -> true
        is PhoneAction.Press, is PhoneAction.OpenApp, is PhoneAction.Wait -> false
    }

    private fun screenSize(active: AccessibilityService): Point {
        val windows = active.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windows.maximumWindowMetrics.bounds.let { Point(it.width(), it.height()) }
        } else {
            @Suppress("DEPRECATION")
            Point().also { windows.defaultDisplay.getRealSize(it) }
        }
    }

    companion object {
        const val NOT_CONNECTED = "Phone control is off. Ask the user to enable MD3 Agent in Settings > Accessibility."
    }
}
