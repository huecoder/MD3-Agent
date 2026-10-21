package dev.huecoder.md3agent.feature.mode

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chat
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Extension
import com.composables.icons.materialsymbols.rounded.Groups
import com.composables.icons.materialsymbols.rounded.Layers
import com.composables.icons.materialsymbols.rounded.Refresh
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.rounded.Tips_and_updates
import com.composables.icons.materialsymbols.rounded.Touch_app
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ModePreset
import dev.huecoder.md3agent.feature.settings.CardGroup
import dev.huecoder.md3agent.feature.settings.CardGroupScope
import dev.huecoder.md3agent.feature.settings.SectionCaption
import dev.huecoder.md3agent.feature.settings.SettingCard
import dev.huecoder.md3agent.feature.settings.SwitchCard
import dev.huecoder.md3agent.feature.settings.settingsPage
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun ModeScreen(onBack: () -> Unit, onRetakeSurvey: () -> Unit, onRetakeOnboarding: () -> Unit) {
    val viewModel = appViewModel { ModeViewModel(it.mode, it.settings) }
    val mode by viewModel.mode.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_mode), onBack) { padding ->
        val current = mode ?: return@SettingsScaffold
        Column(Modifier.settingsPage(padding)) {
            SectionCaption(stringResource(R.string.mode_hint))
            CardGroup {
                ModePreset.entries.forEach { preset ->
                    card(preset) { shape ->
                        SettingCard(
                            icon = preset.icon,
                            title = stringResource(preset.titleRes),
                            summary = stringResource(preset.summaryRes),
                            shape = shape,
                            selected = current.preset == preset,
                            onClick = { viewModel.update { it.with(preset, phoneControl = it.phoneControl) } },
                        )
                    }
                }
            }
            SectionHeader(stringResource(R.string.mode_switches))
            CardGroup {
                switchCard(MaterialSymbols.Rounded.Touch_app, R.string.mode_phone_control, R.string.mode_phone_control_summary, current.phoneControl) { flag ->
                    viewModel.update { it.copy(phoneControl = flag) }
                }
                switchCard(MaterialSymbols.Rounded.Terminal, R.string.mode_shell_and_files, R.string.mode_shell_and_files_summary, current.shellAndFiles) { flag ->
                    viewModel.update { it.copy(shellAndFiles = flag) }
                }
                switchCard(MaterialSymbols.Rounded.Extension, R.string.mode_mcp, R.string.mode_mcp_summary, current.mcp) { flag ->
                    viewModel.update { it.copy(mcp = flag) }
                }
                switchCard(MaterialSymbols.Rounded.Groups, R.string.mode_multiple_agents, R.string.mode_multiple_agents_summary, current.multipleAgents) { flag ->
                    viewModel.update { it.copy(multipleAgents = flag) }
                }
            }
            CardGroup(Modifier.padding(top = RETAKE_TOP)) {
                card { shape ->
                    SettingCard(
                        icon = MaterialSymbols.Rounded.Tips_and_updates,
                        title = stringResource(R.string.mode_retake),
                        summary = stringResource(R.string.mode_retake_summary),
                        shape = shape,
                        onClick = onRetakeSurvey,
                        trailing = { Icon(MaterialSymbols.Rounded.Chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
                card { shape ->
                    SettingCard(
                        icon = MaterialSymbols.Rounded.Refresh,
                        title = stringResource(R.string.mode_onboarding),
                        summary = stringResource(R.string.mode_onboarding_summary),
                        shape = shape,
                        onClick = onRetakeOnboarding,
                        trailing = { Icon(MaterialSymbols.Rounded.Chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
            }
        }
    }
}

private fun CardGroupScope.switchCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes summary: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    card(title) { shape ->
        SwitchCard(
            icon = icon,
            title = stringResource(title),
            summary = stringResource(summary),
            checked = checked,
            onCheckedChange = onChange,
            shape = shape,
        )
    }
}

val ModePreset.icon: ImageVector
    get() = when (this) {
        ModePreset.EVERYDAY -> MaterialSymbols.Rounded.Chat
        ModePreset.BOTH -> MaterialSymbols.Rounded.Layers
        ModePreset.DEVELOPER -> MaterialSymbols.Rounded.Terminal
    }

@get:StringRes
val ModePreset.titleRes: Int
    get() = when (this) {
        ModePreset.EVERYDAY -> R.string.mode_everyday
        ModePreset.BOTH -> R.string.mode_both
        ModePreset.DEVELOPER -> R.string.mode_developer
    }

@get:StringRes
val ModePreset.summaryRes: Int
    get() = when (this) {
        ModePreset.EVERYDAY -> R.string.mode_everyday_summary
        ModePreset.BOTH -> R.string.mode_both_summary
        ModePreset.DEVELOPER -> R.string.mode_developer_summary
    }

private val RETAKE_TOP = 16.dp
