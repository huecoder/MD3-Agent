package dev.huecoder.md3assistant.feature.settings

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
fun McpServerEditorScreen(serverId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "mcp-server-$serverId") { McpServerEditorViewModel(serverId, it.settings, it.mcp) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(if (form?.isNew == false) R.string.mcp_server_title else R.string.mcp_server_title_new),
        onBack = onDone,
        actions = {
            if (form?.isNew == false) {
                IconButton(onClick = { viewModel.delete(onDone) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(R.string.mcp_server_delete))
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
            FormField(
                value = current.name,
                onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
                label = stringResource(R.string.mcp_server_name),
                modifier = Modifier.fillMaxWidth(),
            )
            FormField(
                value = current.url,
                onValueChange = { value -> viewModel.edit { it.copy(url = value) } },
                label = stringResource(R.string.mcp_server_url),
                placeholder = stringResource(R.string.mcp_server_url_placeholder),
                isError = current.urlError != null,
                supportingText = current.urlError?.asString() ?: stringResource(R.string.mcp_server_url_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            SecretField(
                value = current.apiKey,
                onValueChange = { value -> viewModel.edit { it.copy(apiKey = value) } },
                label = stringResource(R.string.mcp_server_token),
            )
            LabeledSwitch(
                title = stringResource(R.string.mcp_server_enabled),
                summary = stringResource(R.string.mcp_server_enabled_summary),
                checked = current.enabled,
                onCheckedChange = { value -> viewModel.edit { it.copy(enabled = value) } },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = viewModel::test,
                    enabled = current.canSave && probe != McpProbeState.Running,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(MaterialSymbols.Rounded.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.mcp_server_test), Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
                if (probe == McpProbeState.Running) LoadingIndicator(Modifier.size(PROBE_INDICATOR_SIZE))
            }
            ProbeResult(probe)

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

@Composable
private fun ProbeResult(probe: McpProbeState) {
    when (probe) {
        is McpProbeState.Found -> Text(
            pluralStringResource(R.plurals.mcp_server_found_tools, probe.toolNames.size, probe.toolNames.size) +
                probe.toolNames.takeIf { it.isNotEmpty() }?.joinToString(prefix = ": ").orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        is McpProbeState.Failed -> Text(probe.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        McpProbeState.Idle, McpProbeState.Running -> Unit
    }
}

private val PROBE_INDICATOR_SIZE = 32.dp
