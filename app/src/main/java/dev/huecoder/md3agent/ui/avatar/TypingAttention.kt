package dev.huecoder.md3agent.ui.avatar

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

@Stable
class TypingAttention {
    var letter: Offset? by mutableStateOf(null)
        private set
    var keystrokes: Int by mutableIntStateOf(0)
        private set

    fun onLetter(positionInRoot: Offset) {
        letter = positionInRoot
        keystrokes++
    }
}
