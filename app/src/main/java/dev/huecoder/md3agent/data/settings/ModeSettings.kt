package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

enum class ModePreset { EVERYDAY, BOTH, DEVELOPER }

@Serializable
data class ModeSettings(
    val onboarded: Boolean = false,
    val phoneControl: Boolean = true,
    val shellAndFiles: Boolean = true,
    val mcp: Boolean = true,
    val multipleAgents: Boolean = true,
) {
    val preset: ModePreset?
        get() = when {
            phoneControl && shellAndFiles && mcp && multipleAgents -> ModePreset.DEVELOPER
            !shellAndFiles && !mcp && multipleAgents -> ModePreset.BOTH
            !shellAndFiles && !mcp && !multipleAgents -> ModePreset.EVERYDAY
            else -> null
        }

    fun with(preset: ModePreset, phoneControl: Boolean): ModeSettings = when (preset) {
        ModePreset.EVERYDAY -> copy(phoneControl = phoneControl, shellAndFiles = false, mcp = false, multipleAgents = false)
        ModePreset.BOTH -> copy(phoneControl = phoneControl, shellAndFiles = false, mcp = false, multipleAgents = true)
        ModePreset.DEVELOPER -> copy(phoneControl = true, shellAndFiles = true, mcp = true, multipleAgents = true)
    }
}
