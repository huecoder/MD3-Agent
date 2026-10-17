package com.mwagent.feature.settings

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.mwagent.ui.haptics.Haptics
import com.mwagent.ui.haptics.rememberHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class SecretTaps(private val scope: CoroutineScope, private val haptics: Haptics, private val onSecret: () -> Unit) {
    val squish = Animatable(1f)
    var remaining: Int? by mutableStateOf(null)
        private set
    private var count = 0
    private var lastTapAt = Long.MIN_VALUE / 2

    fun tap() {
        val now = SystemClock.uptimeMillis()
        count = if (now - lastTapAt <= TAP_WINDOW_MS) count + 1 else 1
        lastTapAt = now
        scope.launch {
            squish.snapTo(SQUISH)
            squish.animateTo(1f, RELEASE_SPEC)
        }
        if (count >= TAPS_TO_OPEN) {
            count = 0
            remaining = null
            haptics.confirm()
            onSecret()
            return
        }
        haptics.tick()
        remaining = (TAPS_TO_OPEN - count).takeIf { count >= TAPS_BEFORE_HINT }
    }

    private companion object {
        const val TAPS_TO_OPEN = 7
        const val TAPS_BEFORE_HINT = 3
        const val TAP_WINDOW_MS = 1_200L
        const val SQUISH = 0.9f
        val RELEASE_SPEC = spring<Float>(dampingRatio = 0.4f, stiffness = 600f)
    }
}

@Composable
fun rememberSecretTaps(onSecret: () -> Unit): SecretTaps {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val currentOnSecret by rememberUpdatedState(onSecret)
    return remember(scope, haptics) { SecretTaps(scope, haptics) { currentOnSecret() } }
}
