package dev.huecoder.md3assistant.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Sync
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.ui.components.FormField
import dev.huecoder.md3assistant.ui.components.LabeledSwitch
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import dev.huecoder.md3assistant.ui.components.appViewModel
import dev.huecoder.md3assistant.ui.text.asString

@Composable
fun VoiceProviderEditorScreen(providerId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "voice-provider-$providerId") { VoiceProviderEditorViewModel(providerId, it.settings, it.modelCatalog) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val chatProviders by viewModel.chatProviders.collectAsStateWithLifecycle()
    val fetch by viewModel.modelFetch.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(if (form?.isNew == false) R.string.voice_provider_title else R.string.voice_provider_title_new),
        onBack = onDone,
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
                .imePadding()
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
                placeholder = stringResource(R.string.provider_base_url_placeholder),
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
                models = current.models.matching(TRANSCRIPTION_HINTS),
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
            FormField(
                value = current.speechVoice,
                onValueChange = { value -> viewModel.edit { it.copy(speechVoice = value) } },
                label = stringResource(R.string.voice_provider_speech_voice),
                supportingText = stringResource(R.string.voice_provider_speech_voice_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = viewModel::fetchModels,
                    enabled = fetch != ModelFetchState.Loading && current.baseUrlError == null && current.baseUrl.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(MaterialSymbols.Rounded.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.provider_fetch_models), Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
                if (fetch == ModelFetchState.Loading) LoadingIndicator(Modifier.size(32.dp))
            }
            (fetch as? ModelFetchState.Failed)?.let {
                Text(it.message.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            LabeledSwitch(
                title = stringResource(R.string.voice_provider_make_active),
                summary = stringResource(R.string.voice_provider_make_active_summary),
                checked = current.makeActive,
                onCheckedChange = { value -> viewModel.edit { it.copy(makeActive = value) } },
            )

            SectionHeader(stringResource(R.string.provider_section_headers))
            HeaderEditor(
                headers = current.headers,
                onChange = { headers -> viewModel.edit { it.copy(headers = headers) } },
            )

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = current.canSave,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            ) { Text(stringResource(R.string.settings_save)) }
        }
    }
}

private fun List<String>.matching(hints: List<String>) = filter { model -> hints.any { model.contains(it, ignoreCase = true) } }.ifEmpty { this }

private val TRANSCRIPTION_HINTS = listOf("whisper", "transcribe", "stt")
private val SPEECH_HINTS = listOf("tts", "speech")
