package dev.huecoder.md3agent.assistant.glow

data class GlowEnvelope(
    val fadeInSeconds: Float = 0.8f,
    val holdSeconds: Float = 2.6f,
    val fadeOutSeconds: Float = 1f,
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
}
