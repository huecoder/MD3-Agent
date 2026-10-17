package dev.huecoder.md3agent.assistant.glow

import dev.huecoder.md3agent.data.settings.AssistantGlow

data class GlowLook(
    val flow: Float,
    val spread: Float,
    val blobs: Float,
    val blobAmount: Float,
    val saturation: Float,
    val dots: Float,
) {
    companion object {
        private val ACCENT = GlowLook(flow = 0.14f, spread = 0.22f, blobs = 2f, blobAmount = 0.65f, saturation = 1f, dots = 0f)
        private val RAINBOW = GlowLook(flow = 0.2f, spread = 0.12f, blobs = 3f, blobAmount = 0.45f, saturation = 1.05f, dots = 0f)
        private const val GRID_AMOUNT = 0.7f

        fun of(glow: AssistantGlow, grid: Boolean): GlowLook {
            val base = if (glow == AssistantGlow.RAINBOW) RAINBOW else ACCENT
            return if (grid) base.copy(dots = GRID_AMOUNT) else base
        }
    }
}
