package dev.huecoder.md3agent.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.feature.settings.DevicePermission
import dev.huecoder.md3agent.system.control.PhoneControl
import dev.huecoder.md3agent.system.files.DeviceFiles
import dev.huecoder.md3agent.system.notifications.AppNotifications
import dev.huecoder.md3agent.system.shell.AccessStatus
import dev.huecoder.md3agent.system.shell.ShellAccess

class PermissionProbe(
    private val context: Context,
    private val phoneControl: PhoneControl,
    private val files: DeviceFiles,
    private val access: ShellAccess,
) {
    suspend fun missing(needs: SurveyNeeds): List<DevicePermission> = buildList {
        if (!AppNotifications.canPost(context)) add(DevicePermission.NOTIFICATIONS)
        if (needs.microphone && !isGranted(Manifest.permission.RECORD_AUDIO)) add(DevicePermission.MICROPHONE)
        if (needs.phoneControl && !phoneControl.isReady()) add(DevicePermission.PHONE_CONTROL)
        if (needs.allFiles && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !files.hasSharedStorageAccess()) add(DevicePermission.ALL_FILES)
        if (needs.termux && access.status(ShellMode.TERMUX, probeRoot = false) != AccessStatus.READY) add(DevicePermission.TERMUX)
    }

    private fun isGranted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
