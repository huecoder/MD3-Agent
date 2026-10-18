package dev.huecoder.md3agent.feature.onboarding.pages

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Notifications
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.rounded.Touch_app
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.onboarding.OnboardingState
import dev.huecoder.md3agent.feature.onboarding.PageScaffold
import dev.huecoder.md3agent.feature.onboarding.SettingsHint
import dev.huecoder.md3agent.feature.onboarding.reveal
import dev.huecoder.md3agent.feature.settings.DeviceAccessViewModel
import dev.huecoder.md3agent.feature.settings.DevicePermission
import dev.huecoder.md3agent.feature.settings.PermissionAction
import dev.huecoder.md3agent.feature.settings.SettingCard
import dev.huecoder.md3agent.feature.settings.rememberPermissionActions
import dev.huecoder.md3agent.system.shell.AccessStatus
import dev.huecoder.md3agent.ui.components.appViewModel
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults

@Composable
fun PermissionsPage(state: OnboardingState, onRefreshed: () -> Unit) {
    val access = appViewModel(key = "onboarding-access") { DeviceAccessViewModel(it.appContext, it.settings, it.shellAccess, it.deviceFiles, it.phoneControl, it.health) }
    val statuses by access.permissions.collectAsStateWithLifecycle()
    val refresh = { access.refresh(); onRefreshed() }
    val actionFor = rememberPermissionActions(refresh = refresh)
    LaunchedEffect(Unit) { access.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    PageScaffold(
        title = stringResource(R.string.onboarding_permissions_title),
        body = stringResource(R.string.onboarding_permissions_body),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.missingPermissions.forEachIndexed { index, permission ->
                val status = statuses[permission] ?: AccessStatus.UNKNOWN
                val ready = status == AccessStatus.READY
                val action: PermissionAction? = actionFor(permission, status)
                SettingCard(
                    icon = permission.icon,
                    title = stringResource(permission.title),
                    summary = stringResource(permission.summary),
                    onClick = action?.onClick?.takeIf { !ready },
                    trailing = {
                        if (ready) {
                            Icon(MaterialSymbols.RoundedFilled.Check_circle, contentDescription = stringResource(R.string.access_status_ready), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            action?.let { FilledTonalButton(onClick = it.onClick, contentPadding = ButtonDefaults.ExtraSmallContentPadding) { Text(stringResource(it.label)) } }
                        }
                    },
                    below = if (permission == DevicePermission.PHONE_CONTROL && !ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        { Text(stringResource(R.string.permission_phone_control_restricted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        null
                    },
                    modifier = Modifier.reveal(minOf(2 + index, 5)),
                )
            }
        }
        SettingsHint(modifier = Modifier.reveal(6))
    }
}

private val DevicePermission.icon: ImageVector
    get() = when (this) {
        DevicePermission.NOTIFICATIONS -> MaterialSymbols.Rounded.Notifications
        DevicePermission.MICROPHONE -> MaterialSymbols.Rounded.Mic
        DevicePermission.PHONE_CONTROL -> MaterialSymbols.Rounded.Touch_app
        DevicePermission.ALL_FILES -> MaterialSymbols.Rounded.Folder
        else -> MaterialSymbols.Rounded.Terminal
    }

@get:StringRes
private val DevicePermission.title: Int
    get() = when (this) {
        DevicePermission.NOTIFICATIONS -> R.string.permission_notifications
        DevicePermission.MICROPHONE -> R.string.permission_microphone
        DevicePermission.PHONE_CONTROL -> R.string.permission_phone_control
        DevicePermission.ALL_FILES -> R.string.access_files_shared
        else -> R.string.access_mode_termux
    }

@get:StringRes
private val DevicePermission.summary: Int
    get() = when (this) {
        DevicePermission.NOTIFICATIONS -> R.string.permission_notifications_summary
        DevicePermission.MICROPHONE -> R.string.permission_microphone_summary
        DevicePermission.PHONE_CONTROL -> R.string.permission_phone_control_summary
        DevicePermission.ALL_FILES -> R.string.access_files_shared_summary
        else -> R.string.access_mode_termux_summary
    }
