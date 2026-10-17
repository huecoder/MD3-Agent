package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class AssistantSettings(
    val layout: AssistantLayout = AssistantLayout.PANEL,
    val showOrb: Boolean = true,
    val glow: AssistantGlow = AssistantGlow.ACCENT,
    val glowGrid: Boolean = false,
)

@Serializable
enum class AssistantLayout { PANEL, COMPACT }

@Serializable
enum class AssistantGlow { OFF, ACCENT, RAINBOW }
