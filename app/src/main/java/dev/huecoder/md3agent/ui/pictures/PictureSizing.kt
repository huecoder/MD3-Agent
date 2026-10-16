package dev.huecoder.md3agent.ui.pictures

import kotlin.math.min

enum class GroupLayout {
    SINGLE,
    ALBUM,
    SLIDER,
    ;

    companion object {
        const val MAX_ALBUM_SIZE = 4

        fun of(count: Int, requested: PictureGrouping?): GroupLayout = when {
            count <= 1 -> SINGLE
            requested == PictureGrouping.ALBUM -> ALBUM
            requested == PictureGrouping.SLIDER -> SLIDER
            count <= MAX_ALBUM_SIZE -> ALBUM
            else -> SLIDER
        }
    }
}

object PictureSizing {
    const val DEFAULT_MAX_WIDTH_DP = 360f
    const val MAX_HEIGHT_DP = 480f
    const val MAX_UPSCALE = 2f

    fun widthCap(availableDp: Float, share: Float?, naturalDp: Float?): Float {
        val wanted = if (share == null) min(availableDp, DEFAULT_MAX_WIDTH_DP) else availableDp * share
        return if (naturalDp == null) wanted else min(wanted, naturalDp * MAX_UPSCALE)
    }
}
