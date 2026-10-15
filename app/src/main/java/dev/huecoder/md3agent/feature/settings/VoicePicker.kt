package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.llm.SpeechVoice
import dev.huecoder.md3agent.ui.haptics.clickHaptics

private const val MAX_SUGGESTIONS = 50

@Composable
fun VoicePicker(
    voiceId: String,
    voiceName: String,
    voices: List<SpeechVoice>,
    onChange: (id: String, name: String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(voiceId, voices) {
        val query = voiceId.trim()
        val matching = if (query.isEmpty() || voices.any { it.id == query }) {
            voices
        } else {
            voices.filter { it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
        }
        matching.take(MAX_SUGGESTIONS)
    }
    val note = voiceName.ifBlank { hint.orEmpty() }

    ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = voiceId,
            onValueChange = {
                onChange(it, "")
                expanded = true
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = note.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            trailingIcon = { if (voices.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }, modifier = Modifier.clickHaptics()) {
            suggestions.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(voice.name)
                            Text(voice.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        onChange(voice.id, voice.name)
                        expanded = false
                    },
                )
            }
        }
    }
}
