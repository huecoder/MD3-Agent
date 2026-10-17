package dev.huecoder.md3agent.assistant.glow

import androidx.compose.ui.graphics.Color
import dev.huecoder.md3agent.data.settings.AssistantGlow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

object GlowPalette {
    const val MAX_STOPS = 5

    private val RAINBOW = listOf(0xFF4285F4, 0xFF9B72CB, 0xFFD96570, 0xFFF4B400, 0xFF34A853).map { Color(it) }

    private const val HUE_SPREAD_DEGREES = 60f
    private const val FULL_TURN_DEGREES = 360f

    fun stops(glow: AssistantGlow, accent: Color): List<Color> = when (glow) {
        AssistantGlow.RAINBOW -> RAINBOW
        else -> accentStops(Oklch.hueOf(accent))
    }

    private fun accentStops(hue: Float): List<Color> = listOf(
        Oklch.color(lightness = 0.85f, chroma = 0.18f, hue = hue),
        Oklch.color(lightness = 0.80f, chroma = 0.17f, hue = hue + HUE_SPREAD_DEGREES),
        Oklch.color(lightness = 0.88f, chroma = 0.10f, hue = hue + HUE_SPREAD_DEGREES / 2),
        Oklch.color(lightness = 0.78f, chroma = 0.19f, hue = hue - HUE_SPREAD_DEGREES / 2),
    )

    internal object Oklch {
        private const val GAMMA_THRESHOLD = 0.04045
        private const val LINEAR_THRESHOLD = 0.0031308

        fun hueOf(color: Color): Float {
            val red = toLinear(color.red.toDouble())
            val green = toLinear(color.green.toDouble())
            val blue = toLinear(color.blue.toDouble())
            val l = cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue)
            val m = cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue)
            val s = cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue)
            val a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
            val b = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
            if (hypot(a, b) < ACHROMATIC_CHROMA) return FALLBACK_HUE
            return ((atan2(b, a) * 180.0 / PI + FULL_TURN_DEGREES) % FULL_TURN_DEGREES).toFloat()
        }

        fun color(lightness: Float, chroma: Float, hue: Float): Color {
            val radians = hue.toDouble() * PI / 180.0
            val a = chroma * cos(radians)
            val b = chroma * sin(radians)
            val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
            val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
            val s = (lightness - 0.0894841775 * a - 1.291485548 * b).pow(3)
            return Color(
                fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s).toFloat(),
                fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s).toFloat(),
                fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s).toFloat(),
            )
        }

        private fun toLinear(channel: Double): Double =
            if (channel <= GAMMA_THRESHOLD) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

        private fun fromLinear(channel: Double): Double {
            val clamped = channel.coerceIn(0.0, 1.0)
            return if (clamped <= LINEAR_THRESHOLD) 12.92 * clamped else 1.055 * clamped.pow(1 / 2.4) - 0.055
        }

        private const val ACHROMATIC_CHROMA = 0.02
        private const val FALLBACK_HUE = 145f
    }
}
