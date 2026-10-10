package dev.huecoder.md3agent.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

@Stable
class BotFace {
    var glyph: EyeGlyph by mutableStateOf(EyeGlyph.Plain)
        private set
    val scale = Animatable(FULL_SCALE)

    suspend fun follow(wanted: () -> EyeGlyph) {
        snapshotFlow(wanted).collectLatest { target ->
            if (target != glyph) {
                scale.animateTo(SWAP_SCALE, VANISH_SPEC)
                glyph = target
            }
            scale.animateTo(FULL_SCALE, POP_SPEC)
        }
    }

    private companion object {
        const val FULL_SCALE = 1f
        const val SWAP_SCALE = 0.15f
        const val VANISH_MS = 32
        val VANISH_SPEC = tween<Float>(VANISH_MS, easing = LinearEasing)
        val POP_SPEC = spring<Float>(dampingRatio = 0.42f, stiffness = 2200f)
    }
}
