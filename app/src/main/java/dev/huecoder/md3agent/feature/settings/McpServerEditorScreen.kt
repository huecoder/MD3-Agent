package dev.huecoder.md3agent.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Key
import com.composables.icons.materialsymbols.rounded.Power_settings_new
import com.composables.icons.materialsymbols.rounded.Verified_user
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.mcp.auth.SignInStatus
import dev.huecoder.md3agent.ui.components.FormField
import dev.huecoder.md3agent.ui.components.SaveAction
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.text.asString

@Composable
fun McpServerEditorScreen(serverId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "mcp-server-$serverId") { McpServerEditorViewModel(serverId, it.settings, it.mcp, it.mcpSignIn) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    val signInStatus by viewModel.signInStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var headersOpen by rememberSaveable { mutableStateOf(false) }

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
        Column(Modifier.settingsPage(padding), verticalArrangement = Arrangement.spacedBy(EDITOR_GROUP_GAP)) {
            CardGroup {
                card { shape ->
                    FieldsCard(shape) {
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
                    }
                }
                card { shape ->
                    SwitchCard(
                        icon = MaterialSymbols.Rounded.Power_settings_new,
                        title = stringResource(R.string.mcp_server_enabled),
                        summary = stringResource(R.string.mcp_server_enabled_summary),
                        checked = current.enabled,
                        onCheckedChange = { value -> viewModel.edit { it.copy(enabled = value) } },
                        shape = shape,
                    )
                }
                card { shape -> ProbeCard(probe, enabled = current.canSave, onTest = viewModel::test, shape = shape) }
            }
            val needsSignIn = current.oauth != null || probe is McpProbeState.NeedsSignIn || signInStatus.serverIdOrNull() == current.id
            if (needsSignIn) {
                CardGroup {
                    card { shape ->
                        SignInCard(
                            isSignedIn = current.oauth != null,
                            status = signInStatus.takeIf { it.serverIdOrNull() == current.id },
                            onSignIn = { viewModel.signIn { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
                            onSignOut = viewModel::signOut,
                            shape = shape,
                        )
                    }
                }
            }
            CardGroup { headersCard(current.headers, headersOpen, { headersOpen = !headersOpen }) { headers -> viewModel.edit { it.copy(headers = headers) } } }
        }
    }
}

@Composable
private fun ProbeCard(probe: McpProbeState, enabled: Boolean, onTest: () -> Unit, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    TestCard(
        title = stringResource(R.string.mcp_server_test),
        summary = when (probe) {
            is McpProbeState.Found -> pluralStringResource(R.plurals.mcp_server_found_tools, probe.toolNames.size, probe.toolNames.size) +
                probe.toolNames.takeIf { it.isNotEmpty() }?.joinToString(prefix = ": ").orEmpty()
            is McpProbeState.Failed -> probe.message
            McpProbeState.Idle, McpProbeState.Running, is McpProbeState.NeedsSignIn -> stringResource(R.string.mcp_server_test_hint)
        },
        summaryColor = when (probe) {
            is McpProbeState.Found -> colors.primary
            is McpProbeState.Failed -> colors.error
            else -> null
        },
        running = probe == McpProbeState.Running,
        enabled = enabled,
        onTest = onTest,
        shape = shape,
    )
}

@Composable
private fun SignInCard(
    isSignedIn: Boolean,
    status: SignInStatus?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    shape: Shape,
) {
    SettingCard(
        icon = if (isSignedIn) MaterialSymbols.Rounded.Verified_user else MaterialSymbols.Rounded.Key,
        title = stringResource(
            when {
                isSignedIn -> R.string.mcp_signed_in
                status is SignInStatus.InProgress -> R.string.mcp_sign_in_waiting
                else -> R.string.mcp_sign_in_needed
            },
        ),
        summary = (status as? SignInStatus.Failed)?.message,
        summaryColor = MaterialTheme.colorScheme.error,
        shape = shape,
        indentBelow = false,
        below = {
            if (isSignedIn) {
                OutlinedButton(onClick = onSignOut, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.mcp_sign_out)) }
            } else {
                Button(onClick = onSignIn, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.mcp_sign_in)) }
            }
        },
    )
}

private fun SignInStatus.serverIdOrNull(): String? = when (this) {
    SignInStatus.Idle -> null
    is SignInStatus.InProgress -> serverId
    is SignInStatus.Failed -> serverId
}
