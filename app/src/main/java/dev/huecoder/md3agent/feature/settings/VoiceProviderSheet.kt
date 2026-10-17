package dev.huecoder.md3agent.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Graphic_eq
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Record_voice_over
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.feature.models.OptionPickerSheet
import dev.huecoder.md3agent.feature.models.PickerOption
import dev.huecoder.md3agent.feature.models.PickerPage
import dev.huecoder.md3agent.llm.SpeechVoice
import dev.huecoder.md3agent.llm.models.ModelInfo
import dev.huecoder.md3agent.llm.models.ModelRoles

internal enum class VoiceSheetTab { RECOGNITION, SPEECH, VOICE }

@Composable
internal fun VoiceProviderSheet(
    initial: VoiceSheetTab,
    form: VoiceProviderForm,
    models: Loaded<ModelInfo>,
    voices: Loaded<SpeechVoice>,
    onEdit: ((VoiceProviderForm) -> VoiceProviderForm) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val labels = remember(resources) { OptionLabels(resources) }
    val recognition = remember(form.format, models.items) { ModelRoles.recognition(form.format, models.items) }
    val speech = remember(form.format, models.items) { ModelRoles.speech(form.format, models.items) }
    val recognitionOptions = remember(recognition, labels) { recognition.suggested.map(labels::model) }
    val recognitionMore = remember(recognition, labels) { recognition.others.map(labels::model) }
    val speechOptions = remember(speech, labels) { speech.suggested.map(labels::model) }
    val speechMore = remember(speech, labels) { speech.others.map(labels::model) }
    val voiceOptions = remember(voices.items, labels) { voices.items.map(labels::voice) }
    val modelsEmpty = stringResource(R.string.picker_models_empty)
    val tabs = listOf(
        PickerPage(
            title = stringResource(R.string.voice_pick_tab_recognition),
            icon = MaterialSymbols.Rounded.Mic,
            selectedId = form.transcriptionModel,
            options = recognitionOptions,
            moreOptions = recognitionMore,
            countLabel = R.plurals.model_picker_count,
            emptyHint = modelsEmpty,
            loading = models.loading && form.format != ApiFormat.ELEVENLABS,
            error = models.error.takeIf { form.format != ApiFormat.ELEVENLABS },
            onRefresh = onRefresh.takeIf { form.format != ApiFormat.ELEVENLABS },
            onPick = { option -> onEdit { it.copy(transcriptionModel = option.id) } },
        ),
        PickerPage(
            title = stringResource(R.string.voice_pick_tab_speech),
            icon = MaterialSymbols.Rounded.Graphic_eq,
            selectedId = form.speechModel,
            options = speechOptions,
            moreOptions = speechMore,
            countLabel = R.plurals.model_picker_count,
            emptyHint = modelsEmpty,
            loading = models.loading,
            error = models.error,
            onRefresh = onRefresh,
            onPick = { option -> onEdit { it.copy(speechModel = option.id) } },
        ),
        PickerPage(
            title = stringResource(R.string.voice_pick_tab_voice),
            icon = MaterialSymbols.Rounded.Record_voice_over,
            selectedId = form.speechVoice,
            options = voiceOptions,
            countLabel = R.plurals.voice_picker_count,
            emptyHint = stringResource(R.string.picker_voices_empty),
            loading = voices.loading,
            error = voices.error,
            onRefresh = onRefresh.takeIf { form.format != ApiFormat.OPENAI },
            onPick = { option -> onEdit { it.withVoice(option) } },
        ),
    )
    OptionPickerSheet(tabs = tabs, initialTab = initial.ordinal, onDismiss = onDismiss)
}

internal fun VoiceProviderForm.withVoice(option: PickerOption) = copy(speechVoice = option.id, speechVoiceName = option.title.takeIf { it != option.id }.orEmpty())
