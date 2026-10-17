package com.mwagent.feature.settings

import android.Manifest
import com.mwagent.system.notifications.AppNotifications
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mwagent.assistant.AssistantRole
import com.mwagent.data.settings.FileScope
import com.mwagent.data.settings.SettingsRepository
import com.mwagent.data.settings.ShellMode
import com.mwagent.system.control.PhoneControl
import com.mwagent.system.files.DeviceFiles
import com.mwagent.system.shell.AccessStatus
import com.mwagent.system.shell.ShellAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DevicePermission { ASSISTANT, MICROPHONE, NOTIFICATIONS, LOCATION, ALL_FILES, PHONE_CONTROL, TERMUX, SHIZUKU, ROOT }

class DeviceAccessViewModel(
    private val context: Context,
    private val settings: SettingsRepository,
    private val access: ShellAccess,
    private val files: DeviceFiles,
    private val phoneControl: PhoneControl,
) : ViewModel() {
    private val _permissions = MutableStateFlow<Map<DevicePermission, AccessStatus>>(emptyMap())
    private val _checkingRoot = MutableStateFlow(false)

    val shellMode: StateFlow<ShellMode?> = settings.settings.map { it.tools.access.shell }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val fileScope: StateFlow<FileScope?> = settings.settings.map { it.tools.access.files }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val permissions: StateFlow<Map<DevicePermission, AccessStatus>> = _permissions.asStateFlow()

    val checkingRoot: StateFlow<Boolean> = _checkingRoot.asStateFlow()

    fun refresh() {
        val local = mapOf(
            DevicePermission.ASSISTANT to readyIf(AssistantRole.isHeld(context)),
            DevicePermission.MICROPHONE to readyIf(isGranted(Manifest.permission.RECORD_AUDIO)),
            DevicePermission.NOTIFICATIONS to readyIf(AppNotifications.canPost(context)),
            DevicePermission.LOCATION to readyIf(
                isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
            ),
            DevicePermission.ALL_FILES to allFilesStatus(),
            DevicePermission.PHONE_CONTROL to phoneControlStatus(),
        )
        _permissions.update { it + local }
        viewModelScope.launch {
            val shells = mapOf(
                DevicePermission.TERMUX to access.status(ShellMode.TERMUX, probeRoot = false),
                DevicePermission.SHIZUKU to access.status(ShellMode.SHIZUKU, probeRoot = false),
            )
            _permissions.update { it + shells + (DevicePermission.ROOT to (it[DevicePermission.ROOT] ?: AccessStatus.UNKNOWN)) }
        }
    }

    fun checkRoot() {
        if (_checkingRoot.value) return
        _checkingRoot.value = true
        viewModelScope.launch {
            try {
                val status = access.status(ShellMode.ROOT, probeRoot = true)
                _permissions.update { it + (DevicePermission.ROOT to status) }
            } finally {
                _checkingRoot.value = false
            }
        }
    }

    fun selectShell(mode: ShellMode) {
        viewModelScope.launch { settings.updateTools { it.copy(access = it.access.copy(shell = mode)) } }
        if (mode == ShellMode.ROOT) checkRootIfUnknown()
    }

    fun selectFiles(scope: FileScope) {
        viewModelScope.launch { settings.updateTools { it.copy(access = it.access.copy(files = scope)) } }
        if (scope == FileScope.ROOT) checkRootIfUnknown()
    }

    fun requestShizukuPermission() {
        viewModelScope.launch {
            access.shizuku.requestPermission()
            refresh()
        }
    }

    private fun checkRootIfUnknown() {
        if (_permissions.value[DevicePermission.ROOT] != AccessStatus.READY) checkRoot()
    }

    private fun allFilesStatus(): AccessStatus = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> AccessStatus.UNAVAILABLE
        files.hasSharedStorageAccess() -> AccessStatus.READY
        else -> AccessStatus.NEEDS_PERMISSION
    }

    private fun phoneControlStatus(): AccessStatus = when {
        phoneControl.isReady() -> AccessStatus.READY
        phoneControl.isEnabledInSettings() -> AccessStatus.NOT_RUNNING
        else -> AccessStatus.NEEDS_PERMISSION
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun readyIf(granted: Boolean) = if (granted) AccessStatus.READY else AccessStatus.NEEDS_PERMISSION
}

fun ShellMode.requiredPermission(): DevicePermission? = when (this) {
    ShellMode.SANDBOX -> null
    ShellMode.TERMUX -> DevicePermission.TERMUX
    ShellMode.SHIZUKU -> DevicePermission.SHIZUKU
    ShellMode.ROOT -> DevicePermission.ROOT
}

fun FileScope.requiredPermission(): DevicePermission? = when (this) {
    FileScope.APP -> null
    FileScope.SHARED_STORAGE -> DevicePermission.ALL_FILES
    FileScope.ROOT -> DevicePermission.ROOT
}
