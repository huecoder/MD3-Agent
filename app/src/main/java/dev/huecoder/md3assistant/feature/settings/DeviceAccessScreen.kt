package dev.huecoder.md3assistant.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
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
import com.composables.icons.materialsymbols.rounded.Adb
import com.composables.icons.materialsymbols.rounded.Assistant
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Shield_lock
import com.composables.icons.materialsymbols.rounded.Smartphone
import com.composables.icons.materialsymbols.rounded.Storage
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.assistant.AssistantRole
import dev.huecoder.md3assistant.data.settings.FileScope
import dev.huecoder.md3assistant.data.settings.ShellMode
import dev.huecoder.md3assistant.system.shell.AccessStatus
import dev.huecoder.md3assistant.system.shell.TermuxShell
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import dev.huecoder.md3assistant.ui.components.ShapeBadge
import dev.huecoder.md3assistant.ui.components.appViewModel
import dev.huecoder.md3assistant.ui.components.copyText
import dev.huecoder.md3assistant.ui.components.groupedListColors
import kotlinx.coroutines.launch

@Composable
fun DeviceAccessScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { DeviceAccessViewModel(it.appContext, it.settings, it.shellAccess, it.deviceFiles) }
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
    val askTermux = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val statusOf = { permission: DevicePermission? -> permission?.let { permissions[it] } ?: AccessStatus.READY }
    val actionFor = { permission: DevicePermission, status: AccessStatus ->
        when (permission) {
            DevicePermission.ASSISTANT -> PermissionAction(R.string.access_action_choose) { AssistantRole.openSystemPicker(context) }
            DevicePermission.MICROPHONE -> if (microphoneDenied) {
                PermissionAction(R.string.access_action_open_settings) { context.openAppDetails() }
            } else {
                PermissionAction(R.string.access_action_grant) { askMicrophone.launch(Manifest.permission.RECORD_AUDIO) }
            }
            DevicePermission.ALL_FILES -> PermissionAction(R.string.access_action_open_settings) { context.openAllFilesAccess() }.takeIf { status != AccessStatus.UNAVAILABLE }
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

    SettingsScaffold(stringResource(R.string.settings_access), onBack) { padding ->
        val shell = shellMode ?: return@SettingsScaffold
        val files = fileScope ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader(stringResource(R.string.access_section_permissions)) }
            item { SectionHint(R.string.access_permissions_hint) }
            itemsIndexed(DevicePermission.entries, key = { _, it -> "permission-${it.name}" }) { index, permission ->
                val status = statusOf(permission)
                PermissionItem(
                    permission = permission,
                    status = status,
                    action = actionFor(permission, status),
                    isBusy = permission == DevicePermission.ROOT && checkingRoot,
                    shapes = ListItemDefaults.segmentedShapes(index, DevicePermission.entries.size),
                )
            }

            item { SectionHeader(stringResource(R.string.access_section_shell)) }
            item { SectionHint(R.string.access_shell_hint) }
            optionItems(ShellMode.entries, shell, { statusOf(it.requiredPermission()) }, viewModel::selectShell) { it.presentation }
            if (shell == ShellMode.TERMUX && statusOf(DevicePermission.TERMUX) == AccessStatus.READY) {
                item { TermuxNote(Modifier.padding(top = 12.dp)) }
            }

            item { SectionHeader(stringResource(R.string.access_section_files)) }
            item { SectionHint(R.string.access_files_hint) }
            optionItems(FileScope.entries, files, { statusOf(it.requiredPermission()) }, viewModel::selectFiles) { it.presentation }
        }
    }
}

private data class PermissionAction(@StringRes val label: Int, val onClick: () -> Unit)

private data class Presentation(@StringRes val title: Int, @StringRes val summary: Int, val icon: ImageVector)

@Composable
private fun SectionHint(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
    )
}

@Composable
private fun PermissionItem(
    permission: DevicePermission,
    status: AccessStatus,
    action: PermissionAction?,
    isBusy: Boolean,
    shapes: ListItemShapes,
) {
    val presentation = permission.presentation
    SegmentedListItem(
        onClick = { if (status != AccessStatus.READY && !isBusy) action?.onClick?.invoke() },
        enabled = status == AccessStatus.READY || action != null,
        shapes = shapes,
        colors = groupedListColors(),
        leadingContent = { ShapeBadge(presentation.icon) },
        supportingContent = {
            Column {
                Text(stringResource(presentation.summary))
                if (status != AccessStatus.READY) StatusLabel(status)
            }
        },
        trailingContent = {
            when {
                isBusy -> LoadingIndicator(Modifier.size(BUSY_INDICATOR_SIZE))
                status == AccessStatus.READY -> Icon(
                    MaterialSymbols.RoundedFilled.Check_circle,
                    contentDescription = stringResource(R.string.access_status_ready),
                    tint = MaterialTheme.colorScheme.primary,
                )
                action != null -> FilledTonalButton(
                    onClick = action.onClick,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                ) { Text(stringResource(action.label)) }
            }
        },
    ) { Text(stringResource(presentation.title), style = MaterialTheme.typography.titleMediumEmphasized) }
}

private fun <T> LazyListScope.optionItems(
    options: List<T>,
    selected: T,
    statusOf: (T) -> AccessStatus,
    onSelect: (T) -> Unit,
    presentationOf: (T) -> Presentation,
) {
    itemsIndexed(options, key = { _, option -> "option-$option" }) { index, option ->
        val presentation = presentationOf(option)
        val status = statusOf(option)
        SegmentedListItem(
            onClick = { onSelect(option) },
            shapes = ListItemDefaults.segmentedShapes(index, options.size),
            colors = groupedListColors(),
            leadingContent = { ShapeBadge(presentation.icon) },
            supportingContent = {
                Column {
                    Text(stringResource(presentation.summary))
                    if (status != AccessStatus.READY) Text(
                        stringResource(R.string.access_needs_setup),
                        color = status.color(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            trailingContent = { RadioButton(selected = option == selected, onClick = null) },
        ) { Text(stringResource(presentation.title), style = MaterialTheme.typography.titleMediumEmphasized) }
    }
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

private val DevicePermission.presentation: Presentation
    get() = when (this) {
        DevicePermission.ASSISTANT -> Presentation(R.string.permission_assistant, R.string.permission_assistant_summary, MaterialSymbols.Rounded.Assistant)
        DevicePermission.MICROPHONE -> Presentation(R.string.permission_microphone, R.string.permission_microphone_summary, MaterialSymbols.Rounded.Mic)
        DevicePermission.ALL_FILES -> Presentation(R.string.permission_all_files, R.string.permission_all_files_summary, MaterialSymbols.Rounded.Storage)
        DevicePermission.TERMUX -> Presentation(R.string.access_mode_termux, R.string.permission_termux_summary, MaterialSymbols.Rounded.Terminal)
        DevicePermission.SHIZUKU -> Presentation(R.string.access_mode_shizuku, R.string.permission_shizuku_summary, MaterialSymbols.Rounded.Adb)
        DevicePermission.ROOT -> Presentation(R.string.access_mode_root, R.string.access_mode_root_summary, MaterialSymbols.Rounded.Shield_lock)
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
