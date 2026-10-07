package dev.huecoder.md3assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.data.settings.AgentProfile
import dev.huecoder.md3assistant.ui.components.FormField
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import kotlinx.coroutines.launch

@Composable
fun AgentSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf(TextFieldValue()) }
    var preview by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val agent = container.settings.current().agent
        name = agent.name
        prompt = TextFieldValue(agent.systemPromptTemplate)
    }

    SettingsScaffold(stringResource(R.string.settings_agent), onBack) { padding ->
        val currentName = name ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(
                value = currentName,
                onValueChange = { name = it },
                label = stringResource(R.string.agent_name),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.agent_system_prompt)) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            )
            Text(
                stringResource(R.string.agent_variables_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                container.systemPrompt.supportedVariables.forEach { variable ->
                    SuggestionChip(onClick = { prompt = prompt.insertAtCursor("{$variable}") }, label = { Text("{$variable}") })
                }
            }
            TextButton(onClick = {
                preview = container.systemPrompt.render(AgentProfile(currentName, prompt.text))
            }) { Text(stringResource(R.string.agent_preview_prompt)) }
            preview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = { prompt = TextFieldValue(AgentProfile.DEFAULT_SYSTEM_PROMPT) }) { Text(stringResource(R.string.agent_reset_prompt)) }

            Button(
                onClick = {
                    scope.launch {
                        container.settings.updateAgent(AgentProfile(currentName.trim().ifEmpty { AgentProfile.DEFAULT_AGENT_NAME }, prompt.text))
                        onBack()
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            ) { Text(stringResource(R.string.settings_save)) }
        }
    }
}

private fun TextFieldValue.insertAtCursor(snippet: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val updated = text.replaceRange(start, end, snippet)
    return copy(text = updated, selection = TextRange(start + snippet.length))
}
