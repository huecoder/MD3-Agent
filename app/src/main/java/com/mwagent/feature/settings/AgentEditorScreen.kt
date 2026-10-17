package com.mwagent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mwagent.App
import com.mwagent.AppContainer
import com.mwagent.R
import com.mwagent.data.settings.AgentProfile
import com.mwagent.data.settings.AgentShape
import com.mwagent.data.settings.ToolSettings
import com.mwagent.data.settings.VoiceSettings
import com.mwagent.ui.avatar.AgentLook
import com.mwagent.ui.components.AgentAvatar
import com.mwagent.ui.components.FormField
import com.mwagent.ui.components.SaveAction
import com.mwagent.ui.components.SearchField
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.rememberSectionFolds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private class AgentDraft(
    val saved: AgentProfile,
    val isNew: Boolean,
    val canDelete: Boolean,
    val nextNumber: Int,
    val baseTools: ToolSettings,
    val voice: VoiceSettings,
)

@Composable
fun AgentEditorScreen(agentId: String?, onDone: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<AgentDraft?>(null) }
    var agent by remember { mutableStateOf(AgentProfile()) }
    var prompt by remember { mutableStateOf(TextFieldValue()) }

    LaunchedEffect(agentId) {
        val current = container.settings.current()
        val existing = current.agents.find(agentId)
        val start = existing ?: AgentProfile(id = UUID.randomUUID().toString(), name = "")
        val count = current.agents.agents.size
        loaded = AgentDraft(
            start,
            isNew = existing == null,
            canDelete = existing != null && count > 1,
            nextNumber = count + 1,
            baseTools = current.tools,
            voice = current.voice,
        )
        agent = start
        prompt = TextFieldValue(start.systemPromptTemplate)
    }

    val newName = loaded?.let { if (it.isNew) stringResource(R.string.agents_new_name, it.nextNumber) else it.saved.name }.orEmpty()
    val draft = agent.copy(name = agent.name.trim().ifEmpty { newName }, systemPromptTemplate = prompt.text)
    val save = loaded?.let { state ->
        SaveAction(isDirty = state.isNew || draft != state.saved, canSave = draft.workingShapes.size >= AgentProfile.MIN_WORKING_SHAPES) {
            scope.launch {
                container.settings.updateAgents { it.upsert(draft) }
                onDone()
            }
        }
    }

    SettingsScaffold(draft.name, onDone, save = save) { padding ->
        val state = loaded ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentPreview(AgentLook.of(draft))
            FormField(
                value = agent.name,
                onValueChange = { agent = agent.copy(name = it) },
                label = stringResource(R.string.agent_name),
                placeholder = newName,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader(stringResource(R.string.agent_shape))
            AgentShapePicker(selected = { it == agent.shape }, tint = agent.tint, onToggle = { agent = agent.copy(shape = it) })
            SectionHeader(stringResource(R.string.agent_color))
            AgentTintPicker(selected = agent.tint, onSelect = { agent = agent.copy(tint = it) })
            SectionHeader(stringResource(R.string.agent_working_shapes))
            HintText(stringResource(R.string.agent_working_hint))
            AgentShapePicker(selected = { it in agent.workingShapes }, tint = agent.tint, onToggle = { agent = agent.toggleWorking(it) })
            SectionHeader(stringResource(R.string.agent_section_voice))
            AgentVoiceSection(state.voice, agent.voice, container.voiceList, onChange = { agent = agent.copy(voice = it) })
            SectionHeader(stringResource(R.string.agent_system_prompt))
            PromptEditor(container, draft, prompt, onPromptChange = { prompt = it })
            SectionHeader(stringResource(R.string.agent_section_tools))
            HintText(stringResource(R.string.agent_tools_hint))
            AgentTools(container, agent, state.baseTools, onChange = { agent = it })
            if (state.canDelete) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            container.settings.updateAgents { it.remove(state.saved.id) }
                            onDone()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                ) { Text(stringResource(R.string.agent_delete)) }
            }
        }
    }
}

@Composable
private fun AgentPreview(look: AgentLook) {
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PREVIEW_PHASE_MS)
            working = !working
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        AgentAvatar(isWorking = working, size = PREVIEW_SIZE, look = look, interactive = true)
    }
}

@Composable
private fun PromptEditor(container: AppContainer, draft: AgentProfile, prompt: TextFieldValue, onPromptChange: (TextFieldValue) -> Unit) {
    var preview by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChange,
        label = { Text(stringResource(R.string.agent_system_prompt)) },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
    )
    HintText(stringResource(R.string.agent_variables_hint))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        container.systemPrompt.supportedVariables.forEach { variable ->
            SuggestionChip(onClick = { onPromptChange(prompt.insertAtCursor("{$variable}")) }, label = { Text("{$variable}") })
        }
    }
    TextButton(onClick = { preview = container.systemPrompt.render(draft) }) { Text(stringResource(R.string.agent_preview_prompt)) }
    preview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    TextButton(onClick = { onPromptChange(TextFieldValue(AgentProfile.DEFAULT_SYSTEM_PROMPT)) }) { Text(stringResource(R.string.agent_reset_prompt)) }
}

@Composable
private fun AgentTools(container: AppContainer, agent: AgentProfile, baseTools: ToolSettings, onChange: (AgentProfile) -> Unit) {
    val effective = agent.tools(baseTools)
    val folds = rememberSectionFolds()
    var query by rememberSaveable { mutableStateOf("") }
    SearchField(query, { query = it }, stringResource(R.string.tools_search_hint))
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        ToolPermissionGroups(
            tools = container.tools.all,
            permissionOf = { container.tools.permission(it, effective) },
            onChange = { tool, permission -> onChange(agent.copy(toolPermissions = agent.toolPermissions + (tool.spec.name to permission))) },
            folds = folds,
            query = query,
        )
    }
    TextButton(onClick = { onChange(agent.copy(toolPermissions = emptyMap())) }, enabled = agent.toolPermissions.isNotEmpty()) {
        Text(stringResource(R.string.agent_tools_follow))
    }
}

@Composable
internal fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun AgentProfile.toggleWorking(shape: AgentShape): AgentProfile {
    if (shape !in workingShapes) return copy(workingShapes = workingShapes + shape)
    if (workingShapes.size <= AgentProfile.MIN_WORKING_SHAPES) return this
    return copy(workingShapes = workingShapes - shape)
}

private fun TextFieldValue.insertAtCursor(snippet: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val updated = text.replaceRange(start, end, snippet)
    return copy(text = updated, selection = TextRange(start + snippet.length))
}

private const val PREVIEW_PHASE_MS = 3_500L
private val PREVIEW_SIZE = 120.dp
