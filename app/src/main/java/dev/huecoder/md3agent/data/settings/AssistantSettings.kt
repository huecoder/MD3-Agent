package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class AssistantSettings(
    val layout: AssistantLayout = AssistantLayout.PANEL,
    val showOrb: Boolean = true,
    val glow: AssistantGlow = AssistantGlow.ACCENT,
    val glowGrid: Boolean = false,
    val glowThickness: Float = DEFAULT_GLOW_THICKNESS,
    val glowSeconds: Float = DEFAULT_GLOW_SECONDS,
    val lockGuard: LockGuard = LockGuard.BIOMETRIC,
) {
    companion object {
        const val MIN_GLOW_THICKNESS = 0.3f
        const val MAX_GLOW_THICKNESS = 1.5f
        const val DEFAULT_GLOW_THICKNESS = 0.75f
        const val MIN_GLOW_SECONDS = 1f
        const val MAX_GLOW_SECONDS = 6f
        const val DEFAULT_GLOW_SECONDS = 2.4f
    }
}

@Serializable
enum class AssistantLayout { PANEL, COMPACT }

@Serializable
enum class AssistantGlow { OFF, ACCENT, RAINBOW }

@Serializable
enum class LockGuard { OFF, BIOMETRIC, UNLOCK }
