package dev.huecoder.md3assistant.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class ChatSettings(
    val showTokenUsage: Boolean = false,
)
