package dev.huecoder.md3assistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3assistant.data.settings.AppSettings
import dev.huecoder.md3assistant.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(settings: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
