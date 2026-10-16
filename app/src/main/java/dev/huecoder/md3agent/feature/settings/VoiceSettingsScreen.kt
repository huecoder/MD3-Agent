package dev.huecoder.md3agent.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Assistant
import com.composables.icons.materialsymbols.rounded.Expand_more
import com.composables.icons.materialsymbols.rounded.Graphic_eq
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Record_voice_over
import com.composables.icons.materialsymbols.rounded.Translate
import com.composables.icons.materialsymbols.rounded.Volume_up
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.InvokeInput
import dev.huecoder.md3agent.data.settings.SpeakReplies
import dev.huecoder.md3agent.data.settings.SpeechOutput
import dev.huecoder.md3agent.data.settings.VoiceProvider
import dev.huecoder.md3agent.data.settings.VoiceSettings
import dev.huecoder.md3agent.ui.components.DropdownOption
import dev.huecoder.md3agent.ui.components.FormField
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.haptics.clickHaptics

@Composable
fun VoiceSettingsScreen(onBack: () -> Unit, onEditProvider: (providerId: String?) -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_voice), onBack) { padding ->
        val voice = settings?.voice ?: return@SettingsScaffold
        VoiceSettingsContent(voice, onEditProvider, viewModel::updateVoice, padding)
    }
}

@Composable
internal fun VoiceSettingsContent(
    voice: VoiceSettings,
    onEditProvider: (String?) -> Unit,
    onUpdate: ((VoiceSettings) -> VoiceSettings) -> Unit,
    padding: PaddingValues,
) {
    var editingLanguage by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item { SectionHeader(stringResource(R.string.voice_settings_section_providers), topPadding = 8.dp) }
        item { Caption(R.string.voice_settings_providers_hint) }
        voiceProviderItems(voice, onEditProvider)

        item { SectionHeader(stringResource(R.string.dictation_settings_section)) }
        val inputs = InputRow.shownFor(voice)
        itemsIndexed(inputs, key = { _, row -> "input-$row" }) { index, row ->
            val shape = ListItemDefaults.segmentedShapes(index, inputs.size).shape
            when (row) {
                InputRow.PROVIDER -> ProviderPicker(
                    icon = MaterialSymbols.Rounded.Mic,
                    title = R.string.voice_settings_transcription_provider,
                    voice = voice,
                    selectedId = voice.transcriptionProvider?.id,
                    shape = shape,
                ) { id -> onUpdate { it.copy(activeProviderId = id) } }
                InputRow.LANGUAGE -> SettingCard(
                    icon = MaterialSymbols.Rounded.Translate,
                    title = stringResource(R.string.dictation_settings_language),
                    summary = voice.language.ifBlank { stringResource(R.string.dictation_settings_language_auto) },
                    shape = shape,
                    onClick = { editingLanguage = true },
                )
                InputRow.INVOKE -> ChoiceCard(
                    icon = MaterialSymbols.Rounded.Assistant,
                    title = R.string.voice_settings_invoke,
                    summary = R.string.voice_settings_invoke_hint,
                    choices = INVOKE_CHOICES,
                    selected = voice.invokeInput,
                    shape = shape,
                ) { value -> onUpdate { it.copy(invokeInput = value) } }
            }
        }

        item { SectionHeader(stringResource(R.string.voice_settings_section_output)) }
        val outputs = OutputRow.shownFor(voice)
        itemsIndexed(outputs, key = { _, row -> "output-$row" }) { index, row ->
            val shape = ListItemDefaults.segmentedShapes(index, outputs.size).shape
            when (row) {
                OutputRow.SPEAK -> ChoiceCard(
                    icon = MaterialSymbols.Rounded.Volume_up,
                    title = R.string.voice_settings_speak,
                    summary = null,
                    choices = SPEAK_CHOICES,
                    selected = voice.speakReplies,
                    shape = shape,
                ) { value -> onUpdate { it.copy(speakReplies = value) } }
                OutputRow.ENGINE -> ChoiceCard(
                    icon = MaterialSymbols.Rounded.Record_voice_over,
                    title = R.string.voice_settings_engine,
                    summary = outputHint(voice),
                    choices = OUTPUT_CHOICES,
                    selected = voice.speechOutput,
                    shape = shape,
                ) { value -> onUpdate { it.copy(speechOutput = value) } }
                OutputRow.PROVIDER -> ProviderPicker(
                    icon = MaterialSymbols.Rounded.Graphic_eq,
                    title = R.string.voice_settings_speech_provider,
                    voice = voice,
                    selectedId = voice.speechProvider?.id,
                    shape = shape,
                ) { id -> onUpdate { it.copy(speechProviderId = id) } }
            }
        }
    }
    if (editingLanguage) {
        LanguageDialog(
            saved = voice.language,
            onConfirm = { language ->
                onUpdate { it.copy(language = language) }
                editingLanguage = false
            },
            onDismiss = { editingLanguage = false },
        )
    }
}

private enum class InputRow {
    PROVIDER, LANGUAGE, INVOKE;

    companion object {
        fun shownFor(voice: VoiceSettings) = entries.filter { it != PROVIDER || voice.providers.isNotEmpty() }
    }
}

private enum class OutputRow {
    SPEAK, ENGINE, PROVIDER;

    companion object {
        fun shownFor(voice: VoiceSettings) = entries.filter { it != PROVIDER || (voice.speechOutput == SpeechOutput.PROVIDER && voice.providers.isNotEmpty()) }
    }
}

@Composable
private fun <T> ChoiceCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes summary: Int?,
    choices: List<Choice<T>>,
    selected: T,
    shape: Shape,
    onSelect: (T) -> Unit,
) {
    SettingCard(
        icon = icon,
        title = stringResource(title),
        summary = summary?.let { stringResource(it) },
        shape = shape,
        below = { ChoicePill(choices, selected, onSelect, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
    )
}

private fun LazyListScope.voiceProviderItems(voice: VoiceSettings, onEditProvider: (String?) -> Unit) {
    val count = voice.providers.size + 1
    val transcriptionId = voice.transcriptionProvider?.id
    val speechId = voice.speechProvider?.id.takeIf { voice.speechOutput == SpeechOutput.PROVIDER }
    itemsIndexed(voice.providers, key = { _, provider -> "voice-${provider.id}" }) { index, provider ->
        SettingCard(
            icon = MaterialSymbols.Rounded.Graphic_eq,
            title = provider.name,
            summary = provider.baseUrl,
            summaryMaxLines = 1,
            shape = ListItemDefaults.segmentedShapes(index, count).shape,
            onClick = { onEditProvider(provider.id) },
            below = roles(provider, transcriptionId, speechId),
        )
    }
    item(key = "add-voice-provider") {
        SettingCard(
            icon = MaterialSymbols.Rounded.Add,
            title = stringResource(R.string.voice_settings_add_provider),
            summary = stringResource(R.string.voice_settings_add_provider_summary),
            shape = ListItemDefaults.segmentedShapes(count - 1, count).shape,
            badgeContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
            badgeContent = MaterialTheme.colorScheme.onSurface,
            onClick = { onEditProvider(null) },
        )
    }
}

private fun roles(provider: VoiceProvider, transcriptionId: String?, speechId: String?): (@Composable ColumnScope.() -> Unit)? {
    val assigned = listOfNotNull(
        R.string.voice_provider_role_transcription.takeIf { provider.id == transcriptionId },
        R.string.voice_provider_role_speech.takeIf { provider.id == speechId },
    )
    if (assigned.isEmpty()) return null
    return {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            assigned.forEach { RolePill(stringResource(it)) }
        }
    }
}

@Composable
private fun RolePill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun Caption(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun ProviderPicker(
    icon: ImageVector,
    @StringRes title: Int,
    voice: VoiceSettings,
    selectedId: String?,
    shape: Shape,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = voice.providers.map { DropdownOption(it.id, it.name) }
    Box {
        SettingCard(
            icon = icon,
            title = stringResource(title),
            summary = options.firstOrNull { it.value == selectedId }?.label,
            shape = shape,
            onClick = { expanded = true },
            trailing = { Icon(MaterialSymbols.Rounded.Expand_more, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.clickHaptics()) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageDialog(saved: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(saved) }
    AlertDialog(
        modifier = Modifier.clickHaptics(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dictation_settings_language)) },
        text = {
            FormField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.dictation_settings_language),
                placeholder = stringResource(R.string.dictation_settings_language_placeholder),
                supportingText = stringResource(R.string.dictation_settings_language_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_chat_cancel)) } },
    )
}

private fun outputHint(voice: VoiceSettings): Int = when {
    voice.speechOutput == SpeechOutput.SYSTEM -> R.string.voice_settings_engine_hint_system
    voice.speechProvider == null -> R.string.voice_settings_engine_needs_provider
    else -> R.string.voice_settings_engine_hint_provider
}

private val CHOICE_HEIGHT = 40.dp

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
