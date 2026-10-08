package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class InterfaceSettings(
    val haptics: Boolean = true,
)
