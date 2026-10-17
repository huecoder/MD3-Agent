package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Graphic_eq
import com.composables.icons.materialsymbols.rounded.Record_voice_over
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.AgentVoice
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.VoiceProvider
import dev.huecoder.md3agent.data.settings.VoiceSettings
import dev.huecoder.md3agent.feature.models.OptionPickerSheet
import dev.huecoder.md3agent.feature.models.PickerPage
import dev.huecoder.md3agent.llm.SpeechVoice
import dev.huecoder.md3agent.llm.VoiceListClient
import dev.huecoder.md3agent.ui.components.DropdownOption

@Composable
internal fun AgentVoiceSection(voice: VoiceSettings, choice: AgentVoice, voiceList: VoiceListClient, onChange: (AgentVoice) -> Unit) {
    val scope = rememberCoroutineScope()
    val loader = remember(scope, voiceList) { CatalogLoader(scope, R.string.provider_voices_fetch_failed) { voiceList.list(it) } }
    val voices by loader.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    val labels = remember(resources) { OptionLabels(resources) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HintText(stringResource(R.string.agent_voice_hint))
        if (voice.providers.isEmpty()) {
            HintText(stringResource(R.string.agent_voice_no_providers))
            return@Column
        }
        val chosenId = choice.providerId.takeIf { id -> voice.providers.any { it.id == id } }
        val provider = voice.speechProviderFor(AgentVoice(providerId = chosenId))
        val defaultProviderLabel = stringResource(R.string.agent_voice_default_provider)
        val providerOptions = buildList<DropdownOption<String?>> {
            add(DropdownOption(null, defaultProviderLabel))
            voice.providers.forEach { add(DropdownOption(it.id, it.name)) }
        }
        CardGroup {
            card { shape ->
                MenuCard(
                    icon = MaterialSymbols.Rounded.Graphic_eq,
                    title = stringResource(R.string.agent_voice_provider),
                    options = providerOptions,
                    selected = chosenId,
                    onSelect = { id -> onChange(AgentVoice(providerId = id)) },
                    shape = shape,
                )
            }
            if (provider != null) {
                card { shape ->
                    val usesProviderVoice = choice.voiceId.isBlank()
                    val voiceId = choice.voiceId.ifBlank { provider.speechVoice }
                    val voiceName = if (usesProviderVoice) provider.speechVoiceName else choice.voiceName
                    PickerCard(
                        icon = MaterialSymbols.Rounded.Record_voice_over,
                        title = stringResource(R.string.agent_voice_voice),
                        value = voiceName.ifBlank { voiceId },
                        detail = if (usesProviderVoice) stringResource(R.string.agent_voice_from_provider) else voiceId.takeIf { voiceName.isNotBlank() },
                        shape = shape,
                        onClick = {
                            loader.load(provider.asEndpointProfile())
                            picking = true
                        },
                    )
                }
            }
        }
        if (provider == null) {
            HintText(stringResource(R.string.agent_voice_system))
        } else {
            TextButton(onClick = { onChange(choice.copy(voiceId = "", voiceName = "")) }, enabled = choice.voiceId.isNotBlank()) {
                Text(stringResource(R.string.agent_voice_reset))
            }
        }
        if (picking && provider != null) {
            AgentVoiceSheet(provider, choice, voices, labels, loader, onChange) { picking = false }
        }
    }
}

@Composable
private fun AgentVoiceSheet(
    provider: VoiceProvider,
    choice: AgentVoice,
    voices: Loaded<SpeechVoice>,
    labels: OptionLabels,
    loader: CatalogLoader<SpeechVoice>,
    onChange: (AgentVoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(voices.items) { voices.items.map(labels::voice) }
    val tab = PickerPage(
        title = stringResource(R.string.voice_pick_tab_voice),
        icon = MaterialSymbols.Rounded.Record_voice_over,
        selectedId = choice.voiceId.ifBlank { provider.speechVoice },
        options = options,
        countLabel = R.plurals.voice_picker_count,
        emptyHint = stringResource(R.string.picker_voices_empty),
        loading = voices.loading,
        error = voices.error,
        onRefresh = { loader.load(provider.asEndpointProfile(), force = true) }.takeIf { provider.format != ApiFormat.OPENAI },
        onPick = { option -> onChange(choice.copy(voiceId = option.id, voiceName = option.title.takeIf { it != option.id }.orEmpty())) },
    )
    OptionPickerSheet(tabs = listOf(tab), initialTab = 0, onDismiss = onDismiss)
}
