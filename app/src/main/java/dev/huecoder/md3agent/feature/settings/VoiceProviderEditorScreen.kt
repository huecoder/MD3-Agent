package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Graphic_eq
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Record_voice_over
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.ui.components.SaveAction
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun VoiceProviderEditorScreen(providerId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "voice-provider-$providerId") { VoiceProviderEditorViewModel(providerId, it.settings, it.modelCatalogClient, it.voiceList) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val chatProviders by viewModel.chatProviders.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<VoiceSheetTab?>(null) }
    var headersOpen by rememberSaveable { mutableStateOf(false) }
    val openSheet = { tab: VoiceSheetTab ->
        viewModel.loadCatalog()
        sheet = tab
    }

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
        Column(Modifier.settingsPage(padding), verticalArrangement = Arrangement.spacedBy(EDITOR_GROUP_GAP)) {
            if (chatProviders.isNotEmpty()) {
                CardGroup { card { shape -> CopyFromChatCard(chatProviders, { source -> viewModel.edit { it.copyEndpoint(source) } }, shape) } }
            }
            CardGroup {
                card { shape ->
                    FormatCard(
                        formats = ApiFormat.VOICE,
                        selected = current.format,
                        hint = stringResource(R.string.voice_provider_format_hint),
                        onSelect = { format -> viewModel.edit { it.withFormat(format) } },
                        shape = shape,
                    )
                }
                card { shape ->
                    EndpointCard(
                        name = current.name,
                        onName = { value -> viewModel.edit { it.copy(name = value) } },
                        baseUrl = current.baseUrl,
                        onBaseUrl = { value -> viewModel.edit { it.copy(baseUrl = value) } },
                        baseUrlError = current.baseUrlError,
                        defaultBaseUrl = current.format.defaultBaseUrl,
                        apiKey = current.apiKey,
                        onApiKey = { value -> viewModel.edit { it.copy(apiKey = value) } },
                        shape = shape,
                    )
                }
            }

            Column {
                SectionHeader(stringResource(R.string.voice_provider_section_models), topPadding = 0.dp)
                CardGroup {
                    card { shape ->
                        PickerCard(
                            icon = MaterialSymbols.Rounded.Mic,
                            title = stringResource(R.string.voice_provider_transcription_model),
                            value = current.transcriptionModel,
                            shape = shape,
                            onClick = { openSheet(VoiceSheetTab.RECOGNITION) },
                        )
                    }
                    card { shape ->
                        PickerCard(
                            icon = MaterialSymbols.Rounded.Graphic_eq,
                            title = stringResource(R.string.voice_provider_speech_model),
                            value = current.speechModel,
                            shape = shape,
                            onClick = { openSheet(VoiceSheetTab.SPEECH) },
                        )
                    }
                    card { shape ->
                        PickerCard(
                            icon = MaterialSymbols.Rounded.Record_voice_over,
                            title = stringResource(R.string.voice_provider_speech_voice),
                            value = current.speechVoiceName.ifBlank { current.speechVoice },
                            detail = current.speechVoice.takeIf { current.speechVoiceName.isNotBlank() },
                            shape = shape,
                            onClick = { openSheet(VoiceSheetTab.VOICE) },
                        )
                    }
                }
            }

            CardGroup { headersCard(current.headers, headersOpen, { headersOpen = !headersOpen }) { headers -> viewModel.edit { it.copy(headers = headers) } } }
        }

        sheet?.let { tab ->
            VoiceProviderSheet(
                initial = tab,
                form = current,
                models = models,
                voices = voices,
                onEdit = viewModel::edit,
                onRefresh = { viewModel.loadCatalog(force = true) },
                onDismiss = { sheet = null },
            )
        }
    }
}

internal val EDITOR_GROUP_GAP = 16.dp
