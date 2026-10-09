package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class InterfaceSettings(
    val haptics: Boolean = true,
    val hapticStrength: Float = DEFAULT_HAPTIC_STRENGTH,
    val swipeRight: SwipeAction = SwipeAction.TRASH,
    val swipeLeft: SwipeAction = SwipeAction.ARCHIVE,
) {
    companion object {
        const val DEFAULT_HAPTIC_STRENGTH = 0.85f
        const val MIN_HAPTIC_STRENGTH = 0.2f
    }
}

@Serializable
enum class SwipeAction { NONE, TRASH, ARCHIVE, MOVE }
