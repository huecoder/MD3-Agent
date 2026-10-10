package dev.huecoder.md3agent.ui.avatar

import androidx.compose.ui.graphics.Path

fun Eye.toPath(scale: Float = 1f, originX: Float = 0f, originY: Float = 0f): Path = Path().apply {
    outlines.forEach { outline ->
        outline.xs.indices.forEach { index ->
            val x = originX + outline.xs[index] * scale
            val y = originY + outline.ys[index] * scale
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
