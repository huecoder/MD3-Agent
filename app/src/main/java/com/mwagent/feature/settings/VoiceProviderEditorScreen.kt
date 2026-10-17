package com.mwagent.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.mwagent.R
import com.mwagent.data.settings.ApiFormat
import com.mwagent.ui.components.FormField
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.SaveAction
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.text.asString

@Composable
fun VoiceProviderEditorScreen(providerId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "voice-provider-$providerId") { VoiceProviderEditorViewModel(providerId, it.settings, it.modelCatalogClient, it.voiceList) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val chatProviders by viewModel.chatProviders.collectAsStateWithLifecycle()
    val fetch by viewModel.modelFetch.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val voiceFetch by viewModel.voiceFetch.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(if (form?.isNew == false) R.string.voice_provider_title else R.string.voice_provider_title_new),
        onBack = onDone,
        save = SaveAction(isDirty, form?.canSave == true) { viewModel.save(onDone) },
        actions = {
            if (form?.isNew == false) {
                IconButton(onClick = { viewModel.delete(onDone) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(R.string.provider_delete))
                }
            }
        },
    ) { padding ->
        val current = form ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (chatProviders.isNotEmpty()) {
                Text(
                    stringResource(R.string.voice_provider_copy_from_chat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chatProviders.forEach { source ->
                        SuggestionChip(onClick = { viewModel.edit { it.copyEndpoint(source) } }, label = { Text(source.name) })
                    }
                }
            }
            ChoiceRow(
                title = stringResource(R.string.provider_format),
                choices = apiFormatChoices(ApiFormat.VOICE),
                selected = current.format,
                onSelect = { format -> viewModel.edit { it.withFormat(format) } },
                hint = stringResource(R.string.voice_provider_format_hint),
            )
            FormField(
                value = current.name,
                onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
                label = stringResource(R.string.provider_name),
                modifier = Modifier.fillMaxWidth(),
            )
            FormField(
                value = current.baseUrl,
                onValueChange = { value -> viewModel.edit { it.copy(baseUrl = value) } },
                label = stringResource(R.string.provider_base_url),
                placeholder = current.format.defaultBaseUrl,
                isError = current.baseUrlError != null,
                supportingText = current.baseUrlError?.asString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            SecretField(
                value = current.apiKey,
                onValueChange = { value -> viewModel.edit { it.copy(apiKey = value) } },
                label = stringResource(R.string.provider_api_key),
            )

            SectionHeader(stringResource(R.string.voice_provider_section_models))
            ModelPicker(
                selected = current.transcriptionModel,
                models = if (current.format == ApiFormat.ELEVENLABS) ELEVENLABS_TRANSCRIPTION_MODELS else current.models.matching(TRANSCRIPTION_HINTS),
                onSelect = { value -> viewModel.edit { it.copy(transcriptionModel = value) } },
                label = stringResource(R.string.voice_provider_transcription_model),
                modifier = Modifier.fillMaxWidth(),
            )
            ModelPicker(
                selected = current.speechModel,
                models = current.models.matching(SPEECH_HINTS),
                onSelect = { value -> viewModel.edit { it.copy(speechModel = value) } },
                label = stringResource(R.string.voice_provider_speech_model),
                modifier = Modifier.fillMaxWidth(),
            )
            VoicePicker(
                voiceId = current.speechVoice,
                voiceName = current.speechVoiceName,
                voices = voices,
                onChange = { id, name -> viewModel.edit { it.copy(speechVoice = id, speechVoiceName = name) } },
                label = stringResource(R.string.voice_provider_speech_voice),
                hint = stringResource(current.format.voiceHint),
                modifier = Modifier.fillMaxWidth(),
            )
            val canFetch = current.baseUrlError == null && current.baseUrl.isNotBlank()
            if (current.format == ApiFormat.ELEVENLABS) {
                FetchButton(R.string.provider_fetch_voices, enabled = canFetch, loading = voiceFetch == ModelFetchState.Loading, onClick = viewModel::fetchVoices)
                FetchError(voiceFetch)
            }
            FetchButton(R.string.provider_fetch_models, enabled = canFetch, loading = fetch == ModelFetchState.Loading, onClick = viewModel::fetchModels)
            FetchError(fetch)

            SectionHeader(stringResource(R.string.provider_section_headers))
            HeaderEditor(
                headers = current.headers,
                onChange = { headers -> viewModel.edit { it.copy(headers = headers) } },
            )
        }
    }
}

private fun List<String>.matching(hints: List<String>) = filter { model -> hints.any { model.contains(it, ignoreCase = true) } }.ifEmpty { this }

private val TRANSCRIPTION_HINTS = listOf("whisper", "transcribe", "stt", "gemini")
private val SPEECH_HINTS = listOf("tts", "speech")
private val ELEVENLABS_TRANSCRIPTION_MODELS = listOf("scribe_v2", "scribe_v2_turbo", "scribe_v2_medical")
