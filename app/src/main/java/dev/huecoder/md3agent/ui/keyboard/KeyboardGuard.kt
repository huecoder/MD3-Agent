package dev.huecoder.md3agent.ui.keyboard

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
fun KeyboardGuard() {
    val focusManager = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime
    LaunchedEffect(windowInfo, focusManager) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (!focused && keyboard.getBottom(density) == 0) focusManager.clearFocus()
        }
    }
}
