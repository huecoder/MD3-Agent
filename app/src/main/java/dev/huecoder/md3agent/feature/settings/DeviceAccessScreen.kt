package dev.huecoder.md3agent.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Touch_app
import com.composables.icons.materialsymbols.rounded.Adb
import com.composables.icons.materialsymbols.rounded.Assistant
import com.composables.icons.materialsymbols.rounded.Battery_android_full
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Notifications
import com.composables.icons.materialsymbols.rounded.Shield_lock
import com.composables.icons.materialsymbols.rounded.Smartphone
import com.composables.icons.materialsymbols.rounded.Storage
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.assistant.AssistantRole
import dev.huecoder.md3agent.data.settings.FileScope
import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.system.control.PhoneControlService
import dev.huecoder.md3agent.system.shell.AccessStatus
import dev.huecoder.md3agent.system.shell.TermuxShell
import dev.huecoder.md3agent.ui.components.FoldableHeader
import dev.huecoder.md3agent.ui.components.SectionFolds
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.components.copyText
import dev.huecoder.md3agent.ui.components.rememberSectionFolds
import kotlinx.coroutines.launch

@Composable
fun DeviceAccessScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { DeviceAccessViewModel(it.appContext, it.settings, it.shellAccess, it.deviceFiles, it.phoneControl, it.health) }
    val shellMode by viewModel.shellMode.collectAsStateWithLifecycle()
    val fileScope by viewModel.fileScope.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val checkingRoot by viewModel.checkingRoot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var microphoneDenied by rememberSaveable { mutableStateOf(false) }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneDenied = !granted
        viewModel.refresh()
    }
    var notificationsDenied by rememberSaveable { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsDenied = !granted
        viewModel.refresh()
    }
    val askTermux = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val actionFor = { permission: DevicePermission, status: AccessStatus ->
        when (permission) {
            DevicePermission.ASSISTANT -> PermissionAction(R.string.access_action_choose) { AssistantRole.openSystemPicker(context) }
            DevicePermission.MICROPHONE -> if (microphoneDenied) {
                PermissionAction(R.string.access_action_open_settings) { context.openAppDetails() }
            } else {
                PermissionAction(R.string.access_action_grant) { askMicrophone.launch(Manifest.permission.RECORD_AUDIO) }
            }
            DevicePermission.NOTIFICATIONS -> if (notificationsDenied || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                PermissionAction(R.string.access_action_open_settings) { context.openNotificationSettings() }
            } else {
                PermissionAction(R.string.access_action_grant) { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            DevicePermission.BACKGROUND_WORK -> PermissionAction(R.string.access_action_grant) { context.requestUnrestrictedBackground() }
            DevicePermission.ALL_FILES -> PermissionAction(R.string.access_action_open_settings) { context.openAllFilesAccess() }.takeIf { status != AccessStatus.UNAVAILABLE }
            DevicePermission.PHONE_CONTROL -> PermissionAction(R.string.access_action_open_settings) { context.openAccessibilitySettings() }
            DevicePermission.TERMUX -> when (status) {
                AccessStatus.NOT_INSTALLED -> PermissionAction(R.string.access_action_install) { context.openUrl(TERMUX_URL) }
                else -> PermissionAction(R.string.access_action_grant) { askTermux.launch(TermuxShell.PERMISSION) }
            }
            DevicePermission.SHIZUKU -> when (status) {
                AccessStatus.NOT_RUNNING -> PermissionAction(R.string.access_action_open) { context.openShizuku() }
                AccessStatus.NEEDS_PERMISSION -> PermissionAction(R.string.access_action_grant, viewModel::requestShizukuPermission)
                else -> null
            }
            DevicePermission.ROOT -> PermissionAction(R.string.access_action_check, viewModel::checkRoot)
        }
    }

    val folds = rememberSectionFolds(*AccessSection.entries.map { it.name }.toTypedArray())

    SettingsScaffold(stringResource(R.string.settings_access), onBack) { padding ->
        val shell = shellMode ?: return@SettingsScaffold
        val files = fileScope ?: return@SettingsScaffold
        DeviceAccessContent(
            state = AccessState(shell, files, permissions, checkingRoot),
            folds = folds,
            actionFor = actionFor,
            onSelectShell = viewModel::selectShell,
            onSelectFiles = viewModel::selectFiles,
            padding = padding,
        )
    }
}

internal data class AccessState(
    val shell: ShellMode,
    val files: FileScope,
    val statuses: Map<DevicePermission, AccessStatus>,
    val checkingRoot: Boolean,
) {
    fun statusOf(permission: DevicePermission?): AccessStatus = permission?.let { statuses[it] } ?: AccessStatus.READY
}

internal enum class AccessSection { PERMISSIONS, COMMANDS, FILES }

private class SystemPermission(val permission: DevicePermission, val presentation: Presentation)

private val SYSTEM_PERMISSIONS = listOf(
    SystemPermission(DevicePermission.ASSISTANT, Presentation(R.string.permission_assistant, R.string.permission_assistant_summary, MaterialSymbols.Rounded.Assistant)),
    SystemPermission(DevicePermission.MICROPHONE, Presentation(R.string.permission_microphone, R.string.permission_microphone_summary, MaterialSymbols.Rounded.Mic)),
    SystemPermission(DevicePermission.NOTIFICATIONS, Presentation(R.string.permission_notifications, R.string.permission_notifications_summary, MaterialSymbols.Rounded.Notifications)),
    SystemPermission(DevicePermission.BACKGROUND_WORK, Presentation(R.string.permission_background_work, R.string.permission_background_work_summary, MaterialSymbols.Rounded.Battery_android_full)),
    SystemPermission(DevicePermission.PHONE_CONTROL, Presentation(R.string.permission_phone_control, R.string.permission_phone_control_summary, MaterialSymbols.Rounded.Touch_app)),
)

@Composable
internal fun DeviceAccessContent(
    state: AccessState,
    folds: SectionFolds,
    actionFor: (DevicePermission, AccessStatus) -> PermissionAction?,
    onSelectShell: (ShellMode) -> Unit,
    onSelectFiles: (FileScope) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        val permissionsOpen = folds.isOpen(AccessSection.PERMISSIONS.name)
        item(key = "header-permissions") {
            FoldableHeader(
                title = stringResource(R.string.access_section_permissions),
                expanded = permissionsOpen,
                onToggle = { folds.toggle(AccessSection.PERMISSIONS.name) },
                summary = stringResource(R.string.access_group_ready, SYSTEM_PERMISSIONS.count { state.statusOf(it.permission) == AccessStatus.READY }, SYSTEM_PERMISSIONS.size),
                modifier = Modifier.animateItem(),
            )
        }
        if (permissionsOpen) {
            item(key = "caption-permissions") { SectionCaption(R.string.access_permissions_hint, Modifier.animateItem()) }
            SYSTEM_PERMISSIONS.forEachIndexed { index, entry ->
                item(key = "permission-${entry.permission.name}") {
                    val status = state.statusOf(entry.permission)
                    PermissionCard(
                        permission = entry.permission,
                        presentation = entry.presentation,
                        status = status,
                        action = actionFor(entry.permission, status),
                        shape = ListItemDefaults.segmentedShapes(index, SYSTEM_PERMISSIONS.size).shape,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        val commandsOpen = folds.isOpen(AccessSection.COMMANDS.name)
        item(key = "header-commands") {
            FoldableHeader(
                title = stringResource(R.string.access_section_shell),
                expanded = commandsOpen,
                onToggle = { folds.toggle(AccessSection.COMMANDS.name) },
                summary = stringResource(state.shell.title),
                modifier = Modifier.animateItem(),
            )
        }
        if (commandsOpen) {
            item(key = "caption-commands") { SectionCaption(R.string.access_shell_hint, Modifier.animateItem()) }
            ShellMode.entries.forEachIndexed { index, mode ->
                item(key = "shell-${mode.name}") {
                    val needed = mode.requiredPermission()
                    val status = state.statusOf(needed)
                    ChoiceCard(
                        presentation = mode.presentation,
                        selected = state.shell == mode,
                        status = status.takeIf { needed != null },
                        action = needed?.let { actionFor(it, status) },
                        isBusy = needed == DevicePermission.ROOT && state.checkingRoot,
                        shape = ListItemDefaults.segmentedShapes(index, ShellMode.entries.size).shape,
                        onSelect = { onSelectShell(mode) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.shell == ShellMode.TERMUX && state.statusOf(DevicePermission.TERMUX) == AccessStatus.READY) {
                item(key = "termux-note") { TermuxNote(Modifier.padding(top = 12.dp).animateItem()) }
            }
        }

        val filesOpen = folds.isOpen(AccessSection.FILES.name)
        item(key = "header-files") {
            FoldableHeader(
                title = stringResource(R.string.access_section_files),
                expanded = filesOpen,
                onToggle = { folds.toggle(AccessSection.FILES.name) },
                summary = stringResource(state.files.title),
                modifier = Modifier.animateItem(),
            )
        }
        if (filesOpen) {
            item(key = "caption-files") { SectionCaption(R.string.access_files_hint, Modifier.animateItem()) }
            FileScope.entries.forEachIndexed { index, scope ->
                item(key = "files-${scope.name}") {
                    val needed = scope.requiredPermission()
                    val status = state.statusOf(needed)
                    ChoiceCard(
                        presentation = scope.presentation,
                        selected = state.files == scope,
                        status = status.takeIf { needed != null },
                        action = needed?.let { actionFor(it, status) },
                        isBusy = needed == DevicePermission.ROOT && state.checkingRoot,
                        shape = ListItemDefaults.segmentedShapes(index, FileScope.entries.size).shape,
                        onSelect = { onSelectFiles(scope) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

internal data class PermissionAction(@StringRes val label: Int, val onClick: () -> Unit)

private data class Presentation(@StringRes val title: Int, @StringRes val summary: Int, val icon: ImageVector)

@Composable
private fun SectionCaption(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun PermissionCard(
    permission: DevicePermission,
    presentation: Presentation,
    status: AccessStatus,
    action: PermissionAction?,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val ready = status == AccessStatus.READY
    SettingCard(
        icon = presentation.icon,
        title = stringResource(presentation.title),
        summary = stringResource(presentation.summary),
        shape = shape,
        onClick = action?.onClick?.takeIf { !ready },
        modifier = modifier,
        trailing = {
            if (ready) {
                Icon(
                    MaterialSymbols.RoundedFilled.Check_circle,
                    contentDescription = stringResource(R.string.access_status_ready),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                action?.let { ActionButton(it) }
            }
        },
        below = if (ready) null else {
            {
                StatusLabel(status)
                if (permission == DevicePermission.PHONE_CONTROL && status == AccessStatus.NEEDS_PERMISSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionHint(R.string.permission_phone_control_restricted)
                }
                if (permission == DevicePermission.BACKGROUND_WORK) PermissionHint(R.string.permission_background_work_hint)
            }
        },
    )
}

@Composable
private fun PermissionHint(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChoiceCard(
    presentation: Presentation,
    selected: Boolean,
    status: AccessStatus?,
    action: PermissionAction?,
    isBusy: Boolean,
    shape: Shape,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingCard(
        icon = presentation.icon,
        title = stringResource(presentation.title),
        summary = stringResource(presentation.summary),
        shape = shape,
        selected = selected,
        onClick = onSelect,
        modifier = modifier,
        trailing = { RadioButton(selected = selected, onClick = null) },
        below = status?.let { current ->
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) { StatusLabel(current) }
                    when {
                        isBusy -> LoadingIndicator(Modifier.size(BUSY_INDICATOR_SIZE))
                        current != AccessStatus.READY -> action?.let { ActionButton(it) }
                    }
                }
            }
        },
    )
}

@Composable
private fun ActionButton(action: PermissionAction) {
    FilledTonalButton(
        onClick = action.onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
    ) { Text(stringResource(action.label)) }
}

@Composable
private fun StatusLabel(status: AccessStatus) {
    Text(stringResource(status.label), color = status.color(), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun TermuxNote(modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.access_setup_termux_ready), style = MaterialTheme.typography.bodyMedium)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        TermuxShell.ENABLE_EXTERNAL_APPS,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    IconButton(onClick = { scope.launch { clipboard.copyText(TermuxShell.ENABLE_EXTERNAL_APPS) } }) {
                        Icon(MaterialSymbols.Rounded.Content_copy, contentDescription = stringResource(R.string.chat_copy))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessStatus.color() = when (this) {
    AccessStatus.READY -> MaterialTheme.colorScheme.primary
    AccessStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.error
}

@get:StringRes
private val AccessStatus.label: Int
    get() = when (this) {
        AccessStatus.READY -> R.string.access_status_ready
        AccessStatus.UNKNOWN -> R.string.access_status_unknown
        AccessStatus.NOT_INSTALLED -> R.string.access_status_not_installed
        AccessStatus.NOT_RUNNING -> R.string.access_status_not_running
        AccessStatus.NEEDS_PERMISSION -> R.string.access_status_needs_permission
        AccessStatus.UNAVAILABLE -> R.string.access_status_unavailable
    }

private val ShellMode.presentation: Presentation
    get() = when (this) {
        ShellMode.SANDBOX -> Presentation(R.string.access_mode_sandbox, R.string.access_mode_sandbox_summary, MaterialSymbols.Rounded.Smartphone)
        ShellMode.TERMUX -> Presentation(R.string.access_mode_termux, R.string.access_mode_termux_summary, MaterialSymbols.Rounded.Terminal)
        ShellMode.SHIZUKU -> Presentation(R.string.access_mode_shizuku, R.string.access_mode_shizuku_summary, MaterialSymbols.Rounded.Adb)
        ShellMode.ROOT -> Presentation(R.string.access_mode_root, R.string.access_shell_root_summary, MaterialSymbols.Rounded.Shield_lock)
    }

@get:StringRes
internal val ShellMode.title: Int
    get() = presentation.title

private val FileScope.presentation: Presentation
    get() = when (this) {
        FileScope.APP -> Presentation(R.string.access_files_app, R.string.access_files_app_summary, MaterialSymbols.Rounded.Folder)
        FileScope.SHARED_STORAGE -> Presentation(R.string.access_files_shared, R.string.access_files_shared_summary, MaterialSymbols.Rounded.Storage)
        FileScope.ROOT -> Presentation(R.string.access_files_root, R.string.access_files_root_summary, MaterialSymbols.Rounded.Shield_lock)
    }

@get:StringRes
internal val FileScope.title: Int
    get() = presentation.title

private fun Context.openAllFilesAccess() {
    val forApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri())
    try {
        startActivity(forApp)
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

private fun Context.openAccessibilitySettings() {
    val service = ComponentName(this, PhoneControlService::class.java).flattenToString()
    val highlighted = Bundle().apply { putString(SETTINGS_FRAGMENT_ARG, service) }
    startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(SETTINGS_FRAGMENT_ARG, service)
            .putExtra(SETTINGS_FRAGMENT_ARGS, highlighted),
    )
}

private fun Context.requestUnrestrictedBackground() {
    try {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun Context.openNotificationSettings() =
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

private fun Context.openAppDetails() =
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))

private fun Context.openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

private fun Context.openShizuku() {
    val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    if (launch != null) startActivity(launch) else openUrl(SHIZUKU_URL)
}

private val BUSY_INDICATOR_SIZE = 32.dp
private const val TERMUX_URL = "https://f-droid.org/packages/com.termux/"
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_URL = "https://shizuku.rikka.app/download/"
private const val SETTINGS_FRAGMENT_ARG = ":settings:fragment_args_key"
private const val SETTINGS_FRAGMENT_ARGS = ":settings:show_fragment_args"
