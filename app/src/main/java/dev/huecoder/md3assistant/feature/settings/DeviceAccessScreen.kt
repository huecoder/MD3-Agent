package dev.huecoder.md3assistant.feature.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.composables.icons.materialsymbols.rounded.Shield_lock
import com.composables.icons.materialsymbols.rounded.Smartphone
import com.composables.icons.materialsymbols.rounded.Terminal
import dev.huecoder.md3assistant.R
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
    val viewModel = appViewModel { DeviceAccessViewModel(it.settings, it.shellAccess) }
    val selected by viewModel.shellMode.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val checkingRoot by viewModel.checkingRoot.collectAsStateWithLifecycle()
    val askTermux = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SettingsScaffold(stringResource(R.string.settings_access), onBack) { padding ->
        val mode = selected ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader(stringResource(R.string.access_section_shell)) }
            item {
                Text(
                    stringResource(R.string.access_shell_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
            itemsIndexed(ShellMode.entries, key = { _, it -> it.name }) { index, option ->
                ShellModeItem(
                    mode = option,
                    status = statuses[option] ?: AccessStatus.UNKNOWN,
                    isSelected = option == mode,
                    index = index,
                    onSelect = { viewModel.select(option) },
                )
            }
            item {
                ShellSetup(
                    mode = mode,
                    status = statuses[mode] ?: AccessStatus.UNKNOWN,
                    checkingRoot = checkingRoot,
                    onGrantTermux = { askTermux.launch(TermuxShell.PERMISSION) },
                    onGrantShizuku = viewModel::requestShizukuPermission,
                    onCheckRoot = viewModel::checkRoot,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ShellModeItem(mode: ShellMode, status: AccessStatus, isSelected: Boolean, index: Int, onSelect: () -> Unit) {
    SegmentedListItem(
        onClick = onSelect,
        shapes = ListItemDefaults.segmentedShapes(index, ShellMode.entries.size),
        colors = groupedListColors(),
        leadingContent = { ShapeBadge(mode.icon) },
        supportingContent = {
            Column {
                Text(stringResource(mode.summary))
                Text(stringResource(status.label), color = status.color(), style = MaterialTheme.typography.labelLarge)
            }
        },
        trailingContent = { RadioButton(selected = isSelected, onClick = null) },
    ) { Text(stringResource(mode.title), style = MaterialTheme.typography.titleMediumEmphasized) }
}

@Composable
private fun ShellSetup(
    mode: ShellMode,
    status: AccessStatus,
    checkingRoot: Boolean,
    onGrantTermux: () -> Unit,
    onGrantShizuku: () -> Unit,
    onCheckRoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    when (mode) {
        ShellMode.SANDBOX -> SetupCard(R.string.access_setup_sandbox, modifier)
        ShellMode.TERMUX -> when (status) {
            AccessStatus.NOT_INSTALLED -> SetupCard(R.string.access_setup_termux_install, modifier) {
                SetupButton(R.string.access_action_get_termux) { context.openUrl(TERMUX_URL) }
            }
            AccessStatus.NEEDS_PERMISSION -> SetupCard(R.string.access_setup_termux_permission, modifier) {
                SetupButton(R.string.access_action_grant, onClick = onGrantTermux)
            }
            else -> SetupCard(R.string.access_setup_termux_ready, modifier) { CopyableCommand(TermuxShell.ENABLE_EXTERNAL_APPS) }
        }
        ShellMode.SHIZUKU -> when (status) {
            AccessStatus.NOT_RUNNING -> SetupCard(R.string.access_setup_shizuku_start, modifier) {
                SetupButton(R.string.access_action_open_shizuku) { context.openShizuku() }
            }
            AccessStatus.NEEDS_PERMISSION -> SetupCard(R.string.access_setup_shizuku_permission, modifier) {
                SetupButton(R.string.access_action_grant, onClick = onGrantShizuku)
            }
            AccessStatus.UNAVAILABLE -> SetupCard(R.string.access_setup_shizuku_old, modifier)
            else -> SetupCard(R.string.access_setup_shizuku_ready, modifier)
        }
        ShellMode.ROOT -> SetupCard(if (status == AccessStatus.READY) R.string.access_setup_root_ready else R.string.access_setup_root, modifier) {
            SetupButton(if (checkingRoot) R.string.access_action_checking else R.string.access_action_check_root, enabled = !checkingRoot, onClick = onCheckRoot)
        }
    }
}

@Composable
private fun SetupCard(@StringRes text: Int, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@Composable
private fun SetupButton(@StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shapes = ButtonDefaults.shapes()) { Text(stringResource(label)) }
}

@Composable
private fun CopyableCommand(command: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            IconButton(onClick = { scope.launch { clipboard.copyText(command) } }) {
                Icon(MaterialSymbols.Rounded.Content_copy, contentDescription = stringResource(R.string.chat_copy))
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

@get:StringRes
internal val ShellMode.title: Int
    get() = when (this) {
        ShellMode.SANDBOX -> R.string.access_mode_sandbox
        ShellMode.TERMUX -> R.string.access_mode_termux
        ShellMode.SHIZUKU -> R.string.access_mode_shizuku
        ShellMode.ROOT -> R.string.access_mode_root
    }

@get:StringRes
private val ShellMode.summary: Int
    get() = when (this) {
        ShellMode.SANDBOX -> R.string.access_mode_sandbox_summary
        ShellMode.TERMUX -> R.string.access_mode_termux_summary
        ShellMode.SHIZUKU -> R.string.access_mode_shizuku_summary
        ShellMode.ROOT -> R.string.access_mode_root_summary
    }

private val ShellMode.icon: ImageVector
    get() = when (this) {
        ShellMode.SANDBOX -> MaterialSymbols.Rounded.Smartphone
        ShellMode.TERMUX -> MaterialSymbols.Rounded.Terminal
        ShellMode.SHIZUKU -> MaterialSymbols.Rounded.Adb
        ShellMode.ROOT -> MaterialSymbols.Rounded.Shield_lock
    }

private fun Context.openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

private fun Context.openShizuku() {
    val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    if (launch != null) startActivity(launch) else openUrl(SHIZUKU_URL)
}

private const val TERMUX_URL = "https://f-droid.org/packages/com.termux/"
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_URL = "https://shizuku.rikka.app/download/"
