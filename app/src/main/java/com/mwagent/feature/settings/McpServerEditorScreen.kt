package com.mwagent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import android.content.Intent
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.composables.icons.materialsymbols.rounded.Key
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import com.mwagent.R
import com.mwagent.mcp.auth.SignInStatus
import com.mwagent.ui.components.FormField
import com.mwagent.ui.components.LabeledSwitch
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.SaveAction
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.text.asString

@Composable
fun McpServerEditorScreen(serverId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "mcp-server-$serverId") { McpServerEditorViewModel(serverId, it.settings, it.mcp, it.mcpSignIn) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    val signInStatus by viewModel.signInStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScaffold(
        title = stringResource(if (form?.isNew == false) R.string.mcp_server_title else R.string.mcp_server_title_new),
        onBack = onDone,
        save = SaveAction(isDirty, form?.canSave == true) { viewModel.save(onDone) },
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
            SignInSection(
                isSignedIn = current.oauth != null,
                needsSignIn = probe is McpProbeState.NeedsSignIn,
                status = signInStatus.takeIf { it.serverIdOrNull() == current.id },
                onSignIn = { viewModel.signIn { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
                onSignOut = viewModel::signOut,
            )

            SectionHeader(stringResource(R.string.provider_section_headers))
            HeaderEditor(
                headers = current.headers,
                onChange = { headers -> viewModel.edit { it.copy(headers = headers) } },
            )
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
        McpProbeState.Idle, McpProbeState.Running, is McpProbeState.NeedsSignIn -> Unit
    }
}

@Composable
private fun SignInSection(isSignedIn: Boolean, needsSignIn: Boolean, status: SignInStatus?, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    if (!isSignedIn && !needsSignIn && status == null) return
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (isSignedIn) MaterialSymbols.RoundedFilled.Check_circle else MaterialSymbols.Rounded.Key,
                    contentDescription = null,
                    tint = if (isSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    stringResource(
                        when {
                            isSignedIn -> R.string.mcp_signed_in
                            status is SignInStatus.InProgress -> R.string.mcp_sign_in_waiting
                            else -> R.string.mcp_sign_in_needed
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            (status as? SignInStatus.Failed)?.let {
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (isSignedIn) {
                OutlinedButton(onClick = onSignOut, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.mcp_sign_out)) }
            } else {
                Button(onClick = onSignIn, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.mcp_sign_in)) }
            }
        }
    }
}

private fun SignInStatus.serverIdOrNull(): String? = when (this) {
    SignInStatus.Idle -> null
    is SignInStatus.InProgress -> serverId
    is SignInStatus.Failed -> serverId
}

private val PROBE_INDICATOR_SIZE = 32.dp
