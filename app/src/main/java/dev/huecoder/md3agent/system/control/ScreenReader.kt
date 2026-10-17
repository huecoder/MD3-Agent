package dev.huecoder.md3agent.system.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.huecoder.md3agent.assistant.AssistantPresence

internal class ScreenReader(private val context: Context, private val assistant: AssistantPresence) {
    @Volatile
    var last: ScreenSnapshot? = null
        private set

    fun screenSize(service: AccessibilityService): Point = service.getSystemService(WindowManager::class.java).realDisplaySize()

    fun frontRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        val covered = assistant.isShowing.value
        val focused = service.rootInActiveWindow
        if (!covered && focused?.window?.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return focused
        return appBelowOverlays(service, skipOwnOverlay = covered)
    }

    private fun appBelowOverlays(service: AccessibilityService, skipOwnOverlay: Boolean): AccessibilityNodeInfo? {
        var ownOverlayPending = skipOwnOverlay
        var fallback: AccessibilityNodeInfo? = null
        for (window in service.windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY || window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val root = window.root ?: continue
            if (ownOverlayPending && root.packageName == context.packageName) {
                ownOverlayPending = false
                continue
            }
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) return root
            if (fallback == null) fallback = root
        }
        return fallback
    }

    fun isOwnAppInFront(service: AccessibilityService): Boolean = frontRoot(service)?.packageName == context.packageName

    fun snapshot(service: AccessibilityService): ScreenSnapshot {
        val size = screenSize(service)
        return ScreenTree.capture(frontRoot(service), size.x, size.y, cutouts(service)).also { last = it }
    }

    fun probe(service: AccessibilityService): ScreenProbe {
        val size = screenSize(service)
        return ScreenTree.probe(frontRoot(service), size.x, size.y)
    }

    private fun cutouts(service: AccessibilityService): List<ScreenBox> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val cutout = service.getSystemService(WindowManager::class.java).maximumWindowMetrics.windowInsets.displayCutout
        return cutout?.boundingRects.orEmpty().map(ScreenBox::of)
    }
}
