package com.mwagent.feature.chat

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

@Stable
class SendEntrance(private val now: () -> Long = SystemClock::uptimeMillis) {
    private var pendingSends = 0
    private var armedAt = 0L

    fun arm() {
        val expired = now() - armedAt > EXPIRES_AFTER_MS
        pendingSends = if (expired) 1 else pendingSends + 1
        armedAt = now()
    }

    fun consume(): Boolean {
        if (now() - armedAt > EXPIRES_AFTER_MS) pendingSends = 0
        if (pendingSends == 0) return false
        pendingSends--
        return true
    }

    companion object {
        const val EXPIRES_AFTER_MS = 2_000L
    }
}

@Composable
fun Modifier.sentEntrance(entrance: SendEntrance?, isNewestUserMessage: Boolean, messageId: String): Modifier {
    val entering = remember(messageId) { isNewestUserMessage && entrance?.consume() == true }
    if (!entering) return this
    val progress = remember(messageId) { Animatable(0f) }
    LaunchedEffect(messageId) { progress.animateTo(1f, ENTRANCE_SPRING) }
    val rise = with(LocalDensity.current) { ENTRANCE_RISE.toPx() }
    return graphicsLayer {
        val travelled = progress.value
        alpha = (travelled * ENTRANCE_FADE_SPEED).coerceIn(0f, 1f)
        translationY = (1f - travelled) * rise
        scaleX = lerp(ENTRANCE_START_SCALE, 1f, travelled)
        scaleY = scaleX
        transformOrigin = TransformOrigin(1f, 1f)
    }
}

private val ENTRANCE_RISE = 64.dp
private const val ENTRANCE_START_SCALE = 0.86f
private const val ENTRANCE_FADE_SPEED = 2.2f
private val ENTRANCE_SPRING = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
