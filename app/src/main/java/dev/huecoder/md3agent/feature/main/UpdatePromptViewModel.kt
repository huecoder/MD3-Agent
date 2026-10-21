package dev.huecoder.md3agent.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.updates.UpdateChecker
import dev.huecoder.md3agent.data.updates.UpdatePromptPolicy
import dev.huecoder.md3agent.data.updates.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdatePromptViewModel(private val updates: UpdateChecker, private val settings: SettingsRepository) : ViewModel() {
    private val _offer = MutableStateFlow<UpdateStatus.Available?>(null)
    val offer: StateFlow<UpdateStatus.Available?> = _offer.asStateFlow()

    init {
        viewModelScope.launch {
            val interfaceSettings = settings.current().ui
            val now = System.currentTimeMillis()
            if (!UpdatePromptPolicy.shouldCheck(now, interfaceSettings.lastUpdateCheckAt)) return@launch
            val status = updates.check()
            if (status is UpdateStatus.Unreachable) return@launch
            settings.updateInterface { it.copy(lastUpdateCheckAt = now) }
            _offer.value = UpdatePromptPolicy.offerFor(status, interfaceSettings.dismissedUpdateVersion)
        }
    }

    fun dismiss() {
        val version = _offer.value?.version ?: return
        _offer.value = null
        viewModelScope.launch { settings.updateInterface { it.copy(dismissedUpdateVersion = version) } }
    }
}
