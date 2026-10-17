package dev.huecoder.md3agent.assistant.glow

import dev.huecoder.md3agent.data.settings.AssistantSettings

data class GlowEnvelope(
    val fadeInSeconds: Float,
    val holdSeconds: Float,
    val fadeOutSeconds: Float,
) {
    val durationSeconds: Float get() = fadeInSeconds + holdSeconds + fadeOutSeconds

    fun at(seconds: Float): Float {
        val rise = smooth(seconds / fadeInSeconds)
        val fall = 1f - smooth((seconds - fadeInSeconds - holdSeconds) / fadeOutSeconds)
        return rise * fall
    }

    private fun smooth(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    companion object {
        private const val RISE_SHARE = 0.25f
        private const val FALL_SHARE = 0.3f
        private const val SWEEP_RISE_SECONDS = 0.2f
        private const val SWEEP_FALL_SECONDS = 0.3f

        fun sweeping(totalSeconds: Float): GlowEnvelope {
            val total = totalSeconds.coerceAtLeast(AssistantSettings.MIN_GLOW_SECONDS)
            return GlowEnvelope(SWEEP_RISE_SECONDS, total - SWEEP_RISE_SECONDS - SWEEP_FALL_SECONDS, SWEEP_FALL_SECONDS)
        }

        fun spanning(totalSeconds: Float): GlowEnvelope {
            val total = totalSeconds.coerceIn(AssistantSettings.MIN_GLOW_SECONDS, AssistantSettings.MAX_GLOW_SECONDS)
            return GlowEnvelope(
                fadeInSeconds = total * RISE_SHARE,
                holdSeconds = total * (1f - RISE_SHARE - FALL_SHARE),
                fadeOutSeconds = total * FALL_SHARE,
            )
        }
    }
}
