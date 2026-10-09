package dev.huecoder.md3agent.feature.playground

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

data class PieceColors(val body: Color, val eyes: Color)

class PlaygroundPalette(accent: Color) {
    private val seed = Hct.fromInt(accent.toArgb())

    fun colors(index: Int): PieceColors {
        val hue = (seed.hue + index * GOLDEN_ANGLE) % FULL_TURN
        val body = Hct.from(hue, seed.chroma, seed.tone)
        val eyeTone = if (seed.tone > LIGHT_TONE) DARK_EYES else LIGHT_EYES
        val eyes = Hct.from(hue, seed.chroma * EYE_CHROMA, eyeTone)
        return PieceColors(Color(body.toInt()), Color(eyes.toInt()))
    }

    private companion object {
        const val GOLDEN_ANGLE = 137.508
        const val FULL_TURN = 360.0
        const val LIGHT_TONE = 55.0
        const val DARK_EYES = 18.0
        const val LIGHT_EYES = 94.0
        const val EYE_CHROMA = 0.5
    }
}
