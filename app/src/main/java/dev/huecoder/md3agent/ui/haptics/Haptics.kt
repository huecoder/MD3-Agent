package dev.huecoder.md3agent.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.App
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Stable
class Haptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    fun tap() = perform(HapticFeedbackType.ContextClick)

    fun confirm() = perform(HapticFeedbackType.Confirm)

    fun tick() = perform(HapticFeedbackType.SegmentTick)

    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

private val LocalHapticsEnabled = compositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}

@Composable
fun ProvideHaptics(content: @Composable () -> Unit) {
    val settings = (LocalContext.current.applicationContext as App).container.settings
    val flags = remember(settings) { settings.settings.map { it.ui.haptics }.distinctUntilChanged() }
    val enabled by flags.collectAsStateWithLifecycle(true)
    CompositionLocalProvider(LocalHapticsEnabled provides enabled, content = content)
}
