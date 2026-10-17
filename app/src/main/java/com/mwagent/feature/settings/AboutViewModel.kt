package com.mwagent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mwagent.data.updates.UpdateChecker
import com.mwagent.data.updates.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Running : UpdateCheck
    data class Done(val status: UpdateStatus) : UpdateCheck
}

class AboutViewModel(private val updates: UpdateChecker) : ViewModel() {
    private val _check = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val check: StateFlow<UpdateCheck> = _check.asStateFlow()

    fun checkForUpdates() {
        if (_check.value == UpdateCheck.Running) return
        _check.value = UpdateCheck.Running
        viewModelScope.launch { _check.value = UpdateCheck.Done(updates.check()) }
    }
}
