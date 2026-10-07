package dev.huecoder.md3assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.data.settings.AppSettings
import dev.huecoder.md3assistant.data.settings.ChatSettings
import dev.huecoder.md3assistant.data.settings.ProviderProfile
import dev.huecoder.md3assistant.data.settings.SpeechSettings
import dev.huecoder.md3assistant.ui.components.FormField
import dev.huecoder.md3assistant.ui.components.LabeledSwitch
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import kotlinx.coroutines.launch

@Composable
fun ChatSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<AppSettings?>(null) }
    var chat by remember { mutableStateOf(ChatSettings()) }
    var speech by remember { mutableStateOf(SpeechSettings()) }

    LaunchedEffect(Unit) {
        val current = container.settings.current()
        chat = current.chat
        speech = current.speech
        loaded = current
    }

    SettingsScaffold(stringResource(R.string.settings_chat_and_voice), onBack) { padding ->
        val settings = loaded ?: return@SettingsScaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.chat_settings_section_replies))
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_show_tokens),
                summary = stringResource(R.string.chat_settings_show_tokens_summary),
                checked = chat.showTokenUsage,
                onCheckedChange = { chat = chat.copy(showTokenUsage = it) },
            )

            SectionHeader(stringResource(R.string.dictation_settings_section))
            Text(
                stringResource(R.string.dictation_settings_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val provider = settings.provider(speech.providerId)
            SpeechProviderPicker(
                providers = settings.providers,
                selected = provider,
                onSelect = { speech = speech.copy(providerId = it?.id) },
            )
            val catalog = (provider ?: settings.provider(settings.activeSelection?.providerId))?.models.orEmpty()
            ModelPicker(
                selected = speech.model,
                models = catalog.filter { model -> SPEECH_MODEL_HINTS.any { model.contains(it, ignoreCase = true) } }.ifEmpty { catalog },
                onSelect = { speech = speech.copy(model = it) },
                modifier = Modifier.fillMaxWidth(),
            )
            FormField(
                value = speech.language,
                onValueChange = { speech = speech.copy(language = it) },
                label = stringResource(R.string.dictation_settings_language),
                placeholder = stringResource(R.string.dictation_settings_language_placeholder),
                supportingText = stringResource(R.string.dictation_settings_language_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        container.settings.updateChat(chat)
                        container.settings.updateSpeech(speech.copy(model = speech.model.trim(), language = speech.language.trim()))
                        onBack()
                    }
                },
                enabled = speech.model.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.settings_save)) }
        }
    }
}

@Composable
private fun SpeechProviderPicker(providers: List<ProviderProfile>, selected: ProviderProfile?, onSelect: (ProviderProfile?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val followChatLabel = stringResource(R.string.dictation_settings_provider_follows_chat)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: followChatLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.dictation_settings_provider)) },
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(followChatLabel) }, onClick = { onSelect(null); expanded = false })
            providers.forEach { provider ->
                DropdownMenuItem(text = { Text(provider.name) }, onClick = { onSelect(provider); expanded = false })
            }
        }
    }
}

private val SPEECH_MODEL_HINTS = listOf("whisper", "transcribe", "stt", "speech")
