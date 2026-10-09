package dev.huecoder.md3agent.ui.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

enum class Primitive { TICK, CLICK, THUD }

data class Pulse(val primitive: Primitive, val scale: Float, val delayMs: Int = 0)

class HapticEngine(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }
    private val composable: Set<Primitive> = supportedPrimitives()

    @Volatile
    var lastPlayedAt: Long = 0L
        private set

    fun play(pulses: List<Pulse>) {
        val target = vibrator?.takeIf { it.hasVibrator() } ?: return
        val audible = pulses.filter { it.scale > 0f }
        if (audible.isEmpty()) return
        lastPlayedAt = SystemClock.uptimeMillis()
        val canCompose = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && audible.all { it.primitive in composable }
        val effect = if (canCompose) composition(audible) else predefined(audible)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            target.vibrate(effect, TOUCH_AUDIO_ATTRIBUTES)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun composition(pulses: List<Pulse>): VibrationEffect {
        val builder = VibrationEffect.startComposition()
        pulses.forEach { pulse -> builder.addPrimitive(pulse.primitive.id, pulse.scale.coerceIn(0f, 1f), pulse.delayMs) }
        return builder.compose()
    }

    private fun predefined(pulses: List<Pulse>): VibrationEffect {
        val strongest = pulses.maxBy { it.scale }
        val effect = when {
            strongest.primitive == Primitive.THUD || strongest.scale >= HEAVY_THRESHOLD -> VibrationEffect.EFFECT_HEAVY_CLICK
            strongest.primitive == Primitive.TICK -> VibrationEffect.EFFECT_TICK
            else -> VibrationEffect.EFFECT_CLICK
        }
        return VibrationEffect.createPredefined(effect)
    }

    private fun supportedPrimitives(): Set<Primitive> {
        val target = vibrator ?: return emptySet()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptySet()
        return Primitive.entries.filter { primitive ->
            primitive.minSdk <= Build.VERSION.SDK_INT && target.areAllPrimitivesSupported(primitive.id)
        }.toSet()
    }

    private val Primitive.id: Int
        get() = when (this) {
            Primitive.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
            Primitive.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
            Primitive.THUD -> VibrationEffect.Composition.PRIMITIVE_THUD
        }

    private val Primitive.minSdk: Int
        get() = if (this == Primitive.THUD) Build.VERSION_CODES.S else Build.VERSION_CODES.R

    private companion object {
        const val HEAVY_THRESHOLD = 0.85f
        val TOUCH_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
