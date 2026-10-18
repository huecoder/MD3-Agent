package dev.huecoder.md3agent.feature.mode

import dev.huecoder.md3agent.data.settings.ModeSettings
import dev.huecoder.md3agent.data.settings.SettingsRepository

class ModeController(private val settings: SettingsRepository) {
    suspend fun update(transform: (ModeSettings) -> ModeSettings) {
        val before = settings.current().mode
        val after = transform(before)
        settings.updateMode { after }
        settings.updateTools { it.followingMode(before, after) }
    }
}
