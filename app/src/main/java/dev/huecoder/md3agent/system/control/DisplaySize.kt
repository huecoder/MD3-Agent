package dev.huecoder.md3agent.system.control

import android.graphics.Point
import android.os.Build
import android.view.WindowManager

fun WindowManager.realDisplaySize(): Point =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        maximumWindowMetrics.bounds.let { Point(it.width(), it.height()) }
    } else {
        @Suppress("DEPRECATION")
        Point().also { defaultDisplay.getRealSize(it) }
    }
