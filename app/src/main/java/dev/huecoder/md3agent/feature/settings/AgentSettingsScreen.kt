package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.AvatarRestShape
import dev.huecoder.md3agent.data.settings.AvatarStyle
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.FormField
import dev.huecoder.md3agent.ui.components.SaveAction
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import kotlinx.coroutines.launch

@Composable
fun AgentSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf(TextFieldValue()) }
    var preview by remember { mutableStateOf<String?>(null) }
    var avatar by remember { mutableStateOf(AvatarStyle.CLASSIC) }
    var restShape by remember { mutableStateOf(AvatarRestShape.CIRCLE) }
    var saved by remember { mutableStateOf<AgentProfile?>(null) }

    LaunchedEffect(Unit) {
        val agent = container.settings.current().agent
        saved = agent
        name = agent.name
        prompt = TextFieldValue(agent.systemPromptTemplate)
        avatar = agent.avatar
        restShape = agent.restShape
    }

    val draft = name?.let { AgentProfile(it.trim().ifEmpty { AgentProfile.DEFAULT_AGENT_NAME }, prompt.text, avatar, restShape) }
    val save = draft?.let { profile ->
        SaveAction(isDirty = profile != saved, canSave = true) {
            scope.launch {
                container.settings.updateAgent(profile)
                saved = profile
                onBack()
            }
        }
    }

    SettingsScaffold(stringResource(R.string.settings_agent), onBack, save = save) { padding ->
        val currentName = name ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            SectionHeader(stringResource(R.string.agent_avatar))
            AvatarPicker(avatar, restShape, onStyle = { avatar = it }, onRestShape = { restShape = it })
            SectionHeader(stringResource(R.string.agent_system_prompt))
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
        }
    }
}

private fun TextFieldValue.insertAtCursor(snippet: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val updated = text.replaceRange(start, end, snippet)
    return copy(text = updated, selection = TextRange(start + snippet.length))
}
