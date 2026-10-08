package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.NetworkSettings
import dev.huecoder.md3agent.net.DohUrl
import dev.huecoder.md3agent.ui.components.LabeledSwitch
import dev.huecoder.md3agent.ui.components.FormField
import dev.huecoder.md3agent.ui.components.SaveAction
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import kotlinx.coroutines.launch

@Composable
fun NetworkSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var network by remember { mutableStateOf<NetworkSettings?>(null) }
    var saved by remember { mutableStateOf<NetworkSettings?>(null) }

    LaunchedEffect(Unit) { container.settings.current().network.let { saved = it; network = it } }

    val save = network?.let { current ->
        val draft = current.copy(dohUrl = current.dohUrl.trim())
        SaveAction(isDirty = draft != saved, canSave = !current.dohEnabled || DohUrl.parse(current.dohUrl) != null) {
            scope.launch {
                container.settings.updateNetwork(draft)
                saved = draft
                onBack()
            }
        }
    }

    SettingsScaffold(stringResource(R.string.settings_network), onBack, save = save) { padding ->
        val current = network ?: return@SettingsScaffold
        val resolvedUrl = DohUrl.parse(current.dohUrl)
        val urlError = current.dohEnabled && current.dohUrl.isNotBlank() && resolvedUrl == null
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledSwitch(
                title = stringResource(R.string.network_doh_title),
                summary = stringResource(R.string.network_doh_summary),
                checked = current.dohEnabled,
                onCheckedChange = { network = current.copy(dohEnabled = it) },
            )
            FormField(
                value = current.dohUrl,
                onValueChange = { network = current.copy(dohUrl = it) },
                label = stringResource(R.string.network_doh_server),
                placeholder = stringResource(R.string.network_doh_server_placeholder),
                enabled = current.dohEnabled,
                isError = urlError,
                supportingText = when {
                    urlError -> stringResource(R.string.network_doh_https_required)
                    resolvedUrl != null -> stringResource(R.string.network_doh_resolved, resolvedUrl)
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.network_doh_bootstrap_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
