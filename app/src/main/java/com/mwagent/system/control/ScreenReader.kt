package com.mwagent.system.control

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

internal class ScreenReader(private val context: Context) {
    @Volatile
    var last: ScreenSnapshot? = null
        private set

    fun screenSize(service: AccessibilityService): Point = service.getSystemService(WindowManager::class.java).realDisplaySize()

    fun frontRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        val focused = service.rootInActiveWindow
        if (focused?.window?.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return focused
        val below = service.windows.filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
        return (below.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } ?: below.firstOrNull())?.root
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
