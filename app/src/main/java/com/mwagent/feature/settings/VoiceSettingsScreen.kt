package com.mwagent.feature.settings

import com.mwagent.ui.components.DropdownOption
import com.mwagent.ui.components.OptionDropdown
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Graphic_eq
import com.mwagent.R
import com.mwagent.data.settings.InvokeInput
import com.mwagent.data.settings.SpeakReplies
import com.mwagent.data.settings.SpeechOutput
import com.mwagent.data.settings.VoiceSettings
import com.mwagent.ui.components.FormField
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.ShapeBadge
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.components.groupedListColors
import kotlinx.coroutines.delay

@Composable
fun VoiceSettingsScreen(onBack: () -> Unit, onEditProvider: (providerId: String?) -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_voice), onBack) { padding ->
        val voice = settings?.voice ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader(stringResource(R.string.voice_settings_section_providers)) }
            item { Hint(stringResource(R.string.voice_settings_providers_hint)) }
            voiceProviderItems(voice, onEditProvider)

            item { SectionHeader(stringResource(R.string.dictation_settings_section)) }
            item {
                SettingsBlock {
                    if (voice.providers.isNotEmpty()) {
                        ProviderDropdown(R.string.voice_settings_transcription_provider, voice, voice.transcriptionProvider?.id) { id ->
                            viewModel.updateVoice { it.copy(activeProviderId = id) }
                        }
                    }
                    LanguageField(voice.language) { language -> viewModel.updateVoice { it.copy(language = language) } }
                    ChoiceRow(
                        title = stringResource(R.string.voice_settings_invoke),
                        choices = INVOKE_CHOICES,
                        selected = voice.invokeInput,
                        onSelect = { value -> viewModel.updateVoice { it.copy(invokeInput = value) } },
                        hint = stringResource(R.string.voice_settings_invoke_hint),
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.voice_settings_section_output)) }
            item {
                SettingsBlock {
                    ChoiceRow(
                        title = stringResource(R.string.voice_settings_speak),
                        choices = SPEAK_CHOICES,
                        selected = voice.speakReplies,
                        onSelect = { value -> viewModel.updateVoice { it.copy(speakReplies = value) } },
                    )
                    ChoiceRow(
                        title = stringResource(R.string.voice_settings_engine),
                        choices = OUTPUT_CHOICES,
                        selected = voice.speechOutput,
                        onSelect = { value -> viewModel.updateVoice { it.copy(speechOutput = value) } },
                        hint = stringResource(outputHint(voice)),
                    )
                    if (voice.speechOutput == SpeechOutput.PROVIDER && voice.providers.isNotEmpty()) {
                        ProviderDropdown(R.string.voice_settings_speech_provider, voice, voice.speechProvider?.id) { id ->
                            viewModel.updateVoice { it.copy(speechProviderId = id) }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.voiceProviderItems(voice: VoiceSettings, onEditProvider: (String?) -> Unit) {
    val count = voice.providers.size + 1
    val transcriptionId = voice.transcriptionProvider?.id
    val speechId = voice.speechProvider?.id.takeIf { voice.speechOutput == SpeechOutput.PROVIDER }
    itemsIndexed(voice.providers, key = { _, provider -> "voice-${provider.id}" }) { index, provider ->
        SegmentedListItem(
            onClick = { onEditProvider(provider.id) },
            shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = groupedListColors(),
            leadingContent = { ShapeBadge(MaterialSymbols.Rounded.Graphic_eq) },
            supportingContent = {
                val roles = listOfNotNull(
                    stringResource(R.string.voice_provider_role_transcription).takeIf { provider.id == transcriptionId },
                    stringResource(R.string.voice_provider_role_speech).takeIf { provider.id == speechId },
                )
                Text(roles.joinToString(" · ").ifEmpty { provider.baseUrl }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
        ) { Text(provider.name, style = MaterialTheme.typography.titleMediumEmphasized) }
    }
    item(key = "add-voice-provider") {
        SegmentedListItem(
            onClick = { onEditProvider(null) },
            shapes = ListItemDefaults.segmentedShapes(count - 1, count),
            colors = groupedListColors(),
            leadingContent = {
                ShapeBadge(
                    MaterialSymbols.Rounded.Add,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            },
        ) { Text(stringResource(R.string.voice_settings_add_provider), style = MaterialTheme.typography.titleMediumEmphasized) }
    }
}

@Composable
private fun SettingsBlock(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun LanguageField(saved: String, onCommit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(saved) }
    val commit by rememberUpdatedState { if (text.trim() != saved) onCommit(text.trim()) }
    LaunchedEffect(text) {
        delay(COMMIT_DELAY_MS)
        commit()
    }
    DisposableEffect(Unit) { onDispose { commit() } }
    FormField(
        value = text,
        onValueChange = { text = it },
        label = stringResource(R.string.dictation_settings_language),
        placeholder = stringResource(R.string.dictation_settings_language_placeholder),
        supportingText = stringResource(R.string.dictation_settings_language_hint),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProviderDropdown(@StringRes label: Int, voice: VoiceSettings, selectedId: String?, onSelect: (String) -> Unit) {
    OptionDropdown(
        label = stringResource(label),
        options = voice.providers.map { DropdownOption(it.id, it.name) },
        selected = selectedId,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun outputHint(voice: VoiceSettings): Int = when {
    voice.speechOutput == SpeechOutput.SYSTEM -> R.string.voice_settings_engine_hint_system
    voice.speechProvider == null -> R.string.voice_settings_engine_needs_provider
    else -> R.string.voice_settings_engine_hint_provider
}

private const val COMMIT_DELAY_MS = 400L

private val INVOKE_CHOICES = listOf(
    Choice(InvokeInput.TEXT, R.string.voice_settings_invoke_text),
    Choice(InvokeInput.VOICE, R.string.voice_settings_invoke_voice),
)

private val SPEAK_CHOICES = listOf(
    Choice(SpeakReplies.NEVER, R.string.voice_speak_never),
    Choice(SpeakReplies.AFTER_VOICE_INPUT, R.string.voice_speak_after_voice),
    Choice(SpeakReplies.ALWAYS, R.string.voice_speak_always),
)

private val OUTPUT_CHOICES = listOf(
    Choice(SpeechOutput.SYSTEM, R.string.voice_output_system),
    Choice(SpeechOutput.PROVIDER, R.string.voice_output_provider),
)
