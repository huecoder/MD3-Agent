package dev.huecoder.md3assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.data.settings.NetworkSettings
import dev.huecoder.md3assistant.net.DohUrl
import dev.huecoder.md3assistant.ui.components.LabeledSwitch
import dev.huecoder.md3assistant.ui.components.FormField
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import kotlinx.coroutines.launch

@Composable
fun NetworkSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var network by remember { mutableStateOf<NetworkSettings?>(null) }

    LaunchedEffect(Unit) { network = container.settings.current().network }

    SettingsScaffold("Сеть", onBack) { padding ->
        val current = network ?: return@SettingsScaffold
        val resolvedUrl = DohUrl.parse(current.dohUrl)
        val urlError = current.dohEnabled && current.dohUrl.isNotBlank() && resolvedUrl == null
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledSwitch(
                title = "Свой DNS (DoH)",
                summary = "Все запросы приложения резолвятся через DNS-over-HTTPS. " +
                    "Помогает, когда сервис режет доступ по региону на уровне DNS.",
                checked = current.dohEnabled,
                onCheckedChange = { network = current.copy(dohEnabled = it) },
            )
            FormField(
                value = current.dohUrl,
                onValueChange = { network = current.copy(dohUrl = it) },
                label = "DoH-сервер",
                placeholder = "dns.example.com или https://host/dns-query",
                enabled = current.dohEnabled,
                isError = urlError,
                supportingText = when {
                    urlError -> "Нужен https-адрес"
                    resolvedUrl != null -> "Запросы пойдут на $resolvedUrl"
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Сам DoH-сервер находится через системный DNS, дальше всё идёт через него.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    scope.launch {
                        container.settings.updateNetwork(current.copy(dohUrl = current.dohUrl.trim()))
                        onBack()
                    }
                },
                enabled = !urlError && !(current.dohEnabled && resolvedUrl == null),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Сохранить") }
        }
    }
}
