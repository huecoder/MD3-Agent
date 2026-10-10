package dev.huecoder.md3agent.ui.avatar

data class GlyphStroke(val startX: Float, val startY: Float, val endX: Float, val endY: Float) {
    fun mirrored() = GlyphStroke(-startX, startY, -endX, endY)
}

enum class EyeGlyph {
    Plain,
    Squint,
    Cross;

    fun strokes(mirrored: Boolean): List<GlyphStroke> {
        val strokes = when (this) {
            Plain -> emptyList()
            Squint -> SQUINT
            Cross -> CROSS
        }
        return if (mirrored) strokes.map(GlyphStroke::mirrored) else strokes
    }

    private companion object {
        const val SQUINT_HALF_WIDTH = 0.16f
        const val SQUINT_HALF_HEIGHT = 0.2f
        const val CROSS_HALF_SIZE = 0.19f

        val SQUINT = listOf(
            GlyphStroke(-SQUINT_HALF_WIDTH, -SQUINT_HALF_HEIGHT, SQUINT_HALF_WIDTH, 0f),
            GlyphStroke(SQUINT_HALF_WIDTH, 0f, -SQUINT_HALF_WIDTH, SQUINT_HALF_HEIGHT),
        )
        val CROSS = listOf(
            GlyphStroke(-CROSS_HALF_SIZE, -CROSS_HALF_SIZE, CROSS_HALF_SIZE, CROSS_HALF_SIZE),
            GlyphStroke(CROSS_HALF_SIZE, -CROSS_HALF_SIZE, -CROSS_HALF_SIZE, CROSS_HALF_SIZE),
        )
    }
}
