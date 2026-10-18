package dev.huecoder.md3agent.feature.mode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.data.settings.ModeSettings
import dev.huecoder.md3agent.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModeViewModel(private val controller: ModeController, settings: SettingsRepository) : ViewModel() {
    val mode: StateFlow<ModeSettings?> = settings.settings
        .map { it.mode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun update(transform: (ModeSettings) -> ModeSettings) {
        viewModelScope.launch { controller.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
