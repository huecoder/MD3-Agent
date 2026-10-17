package com.mwagent.ui.haptics

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwagent.App
import com.mwagent.data.settings.InterfaceSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Stable
class Haptics(private val engine: HapticEngine?, private val strength: Float) {
    fun tick() = play(Pulse(Primitive.TICK, strength))

    fun tap() = play(Pulse(Primitive.CLICK, strength * TAP_SCALE))

    fun press() = play(Pulse(Primitive.CLICK, strength))

    fun confirm() = play(Pulse(Primitive.CLICK, strength), Pulse(Primitive.TICK, strength * ECHO_SCALE, CONFIRM_GAP_MS))

    fun toggle(on: Boolean) = if (on) press() else tick()

    fun impact(power: Float) {
        val clamped = power.coerceIn(0f, 1f)
        val click = Pulse(Primitive.CLICK, strength * (MIN_IMPACT + (1f - MIN_IMPACT) * clamped))
        if (clamped >= THUD_POWER) play(click, Pulse(Primitive.THUD, strength * clamped)) else play(click)
    }

    fun thud() = play(Pulse(Primitive.THUD, strength), Pulse(Primitive.CLICK, strength * ECHO_SCALE, THUD_ECHO_MS))

    fun preview(level: Float) = engine?.play(listOf(Pulse(Primitive.CLICK, level)))

    fun playedRecently(): Boolean =
        engine != null && SystemClock.uptimeMillis() - engine.lastPlayedAt < RECENT_WINDOW_MS

    private fun play(vararg pulses: Pulse) {
        engine?.play(pulses.toList())
    }

    companion object {
        val Silent = Haptics(engine = null, strength = 0f)
        private const val TAP_SCALE = 0.8f
        private const val ECHO_SCALE = 0.55f
        private const val MIN_IMPACT = 0.35f
        private const val THUD_POWER = 0.6f
        private const val CONFIRM_GAP_MS = 70
        private const val THUD_ECHO_MS = 90
        private const val RECENT_WINDOW_MS = 150L
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics.Silent }

@Composable
fun rememberHaptics(): Haptics = LocalHaptics.current

@Composable
fun ProvideHaptics(content: @Composable () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val flags = remember(container) { container.settings.settings.map { it.ui }.distinctUntilChanged() }
    val ui by flags.collectAsStateWithLifecycle(InterfaceSettings())
    val haptics = remember(ui.haptics, ui.hapticStrength) {
        if (ui.haptics) Haptics(container.hapticEngine, ui.hapticStrength) else Haptics.Silent
    }
    CompositionLocalProvider(LocalHaptics provides haptics, content = content)
}
