package dev.huecoder.md3assistant.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3assistant.data.settings.FileScope
import dev.huecoder.md3assistant.data.settings.SettingsRepository
import dev.huecoder.md3assistant.data.settings.ShellMode
import dev.huecoder.md3assistant.system.files.DeviceFiles
import dev.huecoder.md3assistant.system.shell.AccessStatus
import dev.huecoder.md3assistant.system.shell.ShellAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceAccessViewModel(private val settings: SettingsRepository, private val access: ShellAccess, private val files: DeviceFiles) : ViewModel() {
    private val _statuses = MutableStateFlow<Map<ShellMode, AccessStatus>>(emptyMap())
    private val _checkingRoot = MutableStateFlow(false)
    private val _sharedStorage = MutableStateFlow(AccessStatus.UNKNOWN)

    val shellMode: StateFlow<ShellMode?> = settings.settings.map { it.tools.access.shell }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val fileScope: StateFlow<FileScope?> = settings.settings.map { it.tools.access.files }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sharedStorage: StateFlow<AccessStatus> = _sharedStorage.asStateFlow()

    val statuses: StateFlow<Map<ShellMode, AccessStatus>> = _statuses.asStateFlow()

    val checkingRoot: StateFlow<Boolean> = _checkingRoot.asStateFlow()

    fun refresh() {
        _sharedStorage.value = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> AccessStatus.UNAVAILABLE
            files.hasSharedStorageAccess() -> AccessStatus.READY
            else -> AccessStatus.NEEDS_PERMISSION
        }
        viewModelScope.launch {
            val fresh = ShellMode.entries.filter { it != ShellMode.ROOT }.associateWith { access.status(it, probeRoot = false) }
            _statuses.update { it + fresh + (ShellMode.ROOT to (it[ShellMode.ROOT] ?: AccessStatus.UNKNOWN)) }
        }
    }

    fun checkRoot() {
        if (_checkingRoot.value) return
        _checkingRoot.value = true
        viewModelScope.launch {
            try {
                val status = access.status(ShellMode.ROOT, probeRoot = true)
                _statuses.update { it + (ShellMode.ROOT to status) }
            } finally {
                _checkingRoot.value = false
            }
        }
    }

    fun select(mode: ShellMode) {
        viewModelScope.launch { settings.updateTools { it.copy(access = it.access.copy(shell = mode)) } }
        if (mode == ShellMode.ROOT && _statuses.value[ShellMode.ROOT] != AccessStatus.READY) checkRoot()
    }

    fun selectFiles(scope: FileScope) {
        viewModelScope.launch { settings.updateTools { it.copy(access = it.access.copy(files = scope)) } }
        if (scope == FileScope.ROOT && _statuses.value[ShellMode.ROOT] != AccessStatus.READY) checkRoot()
    }

    fun requestShizukuPermission() {
        viewModelScope.launch {
            access.shizuku.requestPermission()
            refresh()
        }
    }
}
