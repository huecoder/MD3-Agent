package dev.huecoder.md3assistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3assistant.data.settings.AppSettings
import dev.huecoder.md3assistant.data.settings.ChatSettings
import dev.huecoder.md3assistant.data.settings.SettingsRepository
import dev.huecoder.md3assistant.data.settings.VoiceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun updateChat(transform: (ChatSettings) -> ChatSettings) {
        viewModelScope.launch { repository.updateChat(transform) }
    }

    fun updateVoice(transform: (VoiceSettings) -> VoiceSettings) {
        viewModelScope.launch { repository.updateVoice(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
