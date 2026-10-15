package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.AgentVoice
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.VoiceSettings
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.SpeechVoice
import dev.huecoder.md3agent.llm.VoiceListClient
import dev.huecoder.md3agent.ui.components.DropdownOption
import dev.huecoder.md3agent.ui.components.OptionDropdown
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.launch

@Composable
internal fun AgentVoiceSection(voice: VoiceSettings, choice: AgentVoice, voiceList: VoiceListClient, onChange: (AgentVoice) -> Unit) {
    val scope = rememberCoroutineScope()
    var fetched by remember { mutableStateOf<List<SpeechVoice>>(emptyList()) }
    var fetch by remember { mutableStateOf<ModelFetchState>(ModelFetchState.Idle) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HintText(stringResource(R.string.agent_voice_hint))
        if (voice.providers.isEmpty()) {
            HintText(stringResource(R.string.agent_voice_no_providers))
            return@Column
        }
        val chosenId = choice.providerId.takeIf { id -> voice.providers.any { it.id == id } }
        OptionDropdown(
            label = stringResource(R.string.agent_voice_provider),
            options = listOf(DropdownOption<String?>(null, stringResource(R.string.agent_voice_default_provider))) +
                voice.providers.map { DropdownOption(it.id, it.name) },
            selected = chosenId,
            onSelect = { id ->
                fetched = emptyList()
                fetch = ModelFetchState.Idle
                onChange(AgentVoice(providerId = id))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val provider = voice.speechProviderFor(AgentVoice(providerId = chosenId))
        if (provider == null) {
            HintText(stringResource(R.string.agent_voice_system))
            return@Column
        }
        VoicePicker(
            voiceId = choice.voiceId,
            voiceName = choice.voiceName,
            voices = fetched,
            onChange = { id, name -> onChange(choice.copy(voiceId = id, voiceName = name)) },
            label = stringResource(R.string.agent_voice_voice),
            placeholder = provider.speechVoice,
            hint = stringResource(provider.format.voiceHint),
            modifier = Modifier.fillMaxWidth(),
        )
        if (provider.format == ApiFormat.ELEVENLABS) {
            FetchButton(R.string.provider_fetch_voices, enabled = true, loading = fetch == ModelFetchState.Loading) {
                fetch = ModelFetchState.Loading
                scope.launch {
                    fetch = try {
                        fetched = voiceList.list(provider.asEndpointProfile())
                        ModelFetchState.Idle
                    } catch (e: ProviderException) {
                        ModelFetchState.Failed(e.message?.let(UiText::Raw) ?: UiText.Resource(R.string.provider_voices_fetch_failed))
                    }
                }
            }
            FetchError(fetch)
        }
    }
}
