package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.LabeledSwitch
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_appearance), onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.agent_avatar))
            AvatarPicker(
                style = current.agent.avatar,
                restShape = current.agent.restShape,
                onStyle = { style -> viewModel.updateAgent { it.copy(avatar = style) } },
                onRestShape = { shape -> viewModel.updateAgent { it.copy(restShape = shape) } },
            )
            SectionHeader(stringResource(R.string.chat_settings_section_replies))
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_show_tokens),
                summary = stringResource(R.string.chat_settings_show_tokens_summary),
                checked = current.chat.showTokenUsage,
                onCheckedChange = { value -> viewModel.updateChat { it.copy(showTokenUsage = value) } },
            )
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_auto_title),
                summary = stringResource(R.string.chat_settings_auto_title_summary),
                checked = current.chat.autoTitle,
                onCheckedChange = { value -> viewModel.updateChat { it.copy(autoTitle = value) } },
            )
            SectionHeader(stringResource(R.string.chat_settings_section_typing))
            TypingSettingsCard(current.chat.typing, onChange = { style -> viewModel.updateChat { it.copy(typing = style) } })
            SectionHeader(stringResource(R.string.appearance_section_feedback))
            LabeledSwitch(
                title = stringResource(R.string.appearance_haptics),
                summary = stringResource(R.string.appearance_haptics_summary),
                checked = current.ui.haptics,
                onCheckedChange = { value -> viewModel.updateInterface { it.copy(haptics = value) } },
            )
            HapticStrengthSlider(
                strength = current.ui.hapticStrength,
                enabled = current.ui.haptics,
                onChange = { value -> viewModel.updateInterface { it.copy(hapticStrength = value) } },
            )
        }
    }
}
