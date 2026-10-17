package com.mwagent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Network_intelligence
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.mwagent.R
import com.mwagent.data.db.MemoryTier
import com.mwagent.data.memory.MemoryStore
import com.mwagent.data.settings.EmbeddingKind
import com.mwagent.data.settings.MemorySettings
import com.mwagent.ui.components.FormField
import com.mwagent.ui.components.SaveAction
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.appViewModel

@Composable
fun MemorySettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { MemoryViewModel(it.settings, it.memories) }
    val embedding by viewModel.embedding.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MemoryDraft?>(null) }

    val save = embedding?.let { current ->
        SaveAction(isDirty, canSave = current.embeddingKind == EmbeddingKind.OFF || current.embeddingsReady) { viewModel.save(onBack) }
    }

    SettingsScaffold(stringResource(R.string.settings_memory), onBack, save = save) { padding ->
        val current = embedding ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader(stringResource(R.string.memory_section_search)) }
            item {
                EmbeddingCard(
                    settings = current,
                    probe = probe,
                    onKind = viewModel::selectKind,
                    onEdit = viewModel::edit,
                    onProbe = viewModel::probeEmbeddings,
                )
            }
            memorySection(
                tier = MemoryTier.INSTANT,
                hint = { stringResource(R.string.memory_instant_hint, lists.instant.size, MemoryStore.MAX_INSTANT) },
                memories = lists.instant,
                onOpen = { editing = it },
            )
            memorySection(
                tier = MemoryTier.LONG_TERM,
                hint = { stringResource(R.string.memory_long_term_hint) },
                memories = lists.longTerm,
                onOpen = { editing = it },
            )
        }
    }

    editing?.let { draft ->
        MemoryEditorDialog(
            initial = draft,
            onSubmit = viewModel::submit,
            onDelete = draft.id?.let { id -> { viewModel.delete(id); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EmbeddingCard(
    settings: MemorySettings,
    probe: EmbeddingProbe,
    onKind: (EmbeddingKind) -> Unit,
    onEdit: ((MemorySettings) -> MemorySettings) -> Unit,
    onProbe: () -> Unit,
) {
    val kind = settings.embeddingKind
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.memory_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedChoices(KIND_CHOICES, kind, onKind)
            if (kind != EmbeddingKind.GEMINI) GeminiTip(onUse = { onKind(EmbeddingKind.GEMINI) })
            if (kind == EmbeddingKind.OFF) return@Column
            SecretField(
                value = settings.embeddingApiKey,
                onValueChange = { value -> onEdit { it.copy(embeddingApiKey = value) } },
                label = stringResource(R.string.memory_api_key),
            )
            if (kind == EmbeddingKind.GEMINI) GetKeyButton()
            FormField(
                value = settings.embeddingModel,
                onValueChange = { value -> onEdit { it.copy(embeddingModel = value) } },
                label = stringResource(R.string.memory_model),
                placeholder = if (kind == EmbeddingKind.GEMINI) MemorySettings.GEMINI_MODEL else stringResource(R.string.memory_model_placeholder_openai),
                supportingText = if (kind == EmbeddingKind.GEMINI) stringResource(R.string.memory_optional_default) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind == EmbeddingKind.OPENAI) {
                FormField(
                    value = settings.embeddingBaseUrl,
                    onValueChange = { value -> onEdit { it.copy(embeddingBaseUrl = value) } },
                    label = stringResource(R.string.memory_base_url),
                    placeholder = stringResource(R.string.memory_base_url_placeholder_openai),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ProbeRow(probe, enabled = settings.embeddingsReady, onProbe = onProbe)
        }
    }
}

@Composable
private fun GeminiTip(onUse: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MaterialSymbols.Rounded.Network_intelligence, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    stringResource(R.string.memory_gemini_tip_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                stringResource(R.string.memory_gemini_tip_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onUse, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.memory_gemini_tip_use)) }
                GetKeyButton()
            }
        }
    }
}

@Composable
private fun GetKeyButton() {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openUri(MemorySettings.GEMINI_KEY_URL) }, shapes = ButtonDefaults.shapes()) {
        Icon(MaterialSymbols.Rounded.Open_in_new, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(R.string.memory_get_key), Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

@Composable
private fun ProbeRow(probe: EmbeddingProbe, enabled: Boolean, onProbe: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = onProbe, enabled = enabled && probe != EmbeddingProbe.Running, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.memory_probe))
        }
        when (probe) {
            EmbeddingProbe.Running -> LoadingIndicator(Modifier.size(32.dp))
            is EmbeddingProbe.Ready -> Text(
                stringResource(R.string.memory_probe_ok, probe.dimensions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            is EmbeddingProbe.Failed -> Text(
                probe.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            EmbeddingProbe.Idle -> Unit
        }
    }
}

private val KIND_CHOICES = listOf(
    Choice(EmbeddingKind.OFF, R.string.memory_kind_off),
    Choice(EmbeddingKind.GEMINI, R.string.memory_kind_gemini),
    Choice(EmbeddingKind.OPENAI, R.string.memory_kind_openai),
)
