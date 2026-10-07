package dev.huecoder.md3assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.data.settings.SearchBackend
import dev.huecoder.md3assistant.data.settings.SearchSettings
import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.data.settings.WeatherSettings
import dev.huecoder.md3assistant.ui.components.FormField
import dev.huecoder.md3assistant.ui.components.LabeledSwitch
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import dev.huecoder.md3assistant.ui.components.ShapeBadge
import dev.huecoder.md3assistant.ui.components.ToolCatalog
import kotlinx.coroutines.launch

@Composable
fun ToolSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var tools by remember { mutableStateOf<ToolSettings?>(null) }

    LaunchedEffect(Unit) { tools = container.settings.current().tools }

    SettingsScaffold("Инструменты", onBack) { padding ->
        val current = tools ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader("Доступны модели")
            container.tools.all.forEach { tool ->
                val name = tool.spec.name
                val info = ToolCatalog.of(name)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShapeBadge(info.icon, size = BADGE_SIZE)
                    LabeledSwitch(
                        title = info.title,
                        summary = info.summary,
                        checked = current.isEnabled(name),
                        onCheckedChange = { enabled ->
                            tools = current.copy(disabledTools = if (enabled) current.disabledTools - name else current.disabledTools + name)
                        },
                    )
                }
            }

            SectionHeader("Поиск")
            SearchSection(current.search) { tools = current.copy(search = it) }

            SectionHeader("Погода")
            WeatherSection(current.weather) { tools = current.copy(weather = it) }

            Button(
                onClick = {
                    scope.launch {
                        container.settings.updateTools(current.trimmed())
                        onBack()
                    }
                },
                enabled = current.search.isComplete(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            ) { Text("Сохранить") }
        }
    }
}

@Composable
private fun SearchSection(search: SearchSettings, onChange: (SearchSettings) -> Unit) {
    val backends = SearchBackend.entries
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            backends.forEachIndexed { index, backend ->
                SegmentedButton(
                    selected = search.backend == backend,
                    onClick = { onChange(search.copy(backend = backend)) },
                    shape = SegmentedButtonDefaults.itemShape(index, backends.size),
                    label = { Text(backend.label) },
                )
            }
        }
        Text(search.backend.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (search.backend) {
            SearchBackend.HTML_SCRAPE -> Unit
            SearchBackend.SEARXNG -> FormField(
                value = search.searxngUrl,
                onValueChange = { onChange(search.copy(searxngUrl = it)) },
                label = "Адрес SearXNG",
                placeholder = "https://searx.example.com или http://127.0.0.1:8888",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            SearchBackend.BRAVE_API -> FormField(
                value = search.apiKey,
                onValueChange = { onChange(search.copy(apiKey = it)) },
                label = "API-ключ",
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WeatherSection(weather: WeatherSettings, onChange: (WeatherSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Любой сервис с API как у Open-Meteo: публичный или свой. Ключ не нужен.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormField(
            value = weather.forecastUrl,
            onValueChange = { onChange(weather.copy(forecastUrl = it)) },
            label = "Адрес прогноза",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        FormField(
            value = weather.geocodingUrl,
            onValueChange = { onChange(weather.copy(geocodingUrl = it)) },
            label = "Адрес поиска городов",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val SearchBackend.label: String
    get() = when (this) {
        SearchBackend.HTML_SCRAPE -> "Без ключа"
        SearchBackend.SEARXNG -> "SearXNG"
        SearchBackend.BRAVE_API -> "Brave API"
    }

private val SearchBackend.hint: String
    get() = when (this) {
        SearchBackend.HTML_SCRAPE -> "Парсит HTML-выдачу DuckDuckGo. Работает сразу, но иногда упирается в капчу."
        SearchBackend.SEARXNG -> "Свой или публичный инстанс. В его настройках должен быть включён формат JSON."
        SearchBackend.BRAVE_API -> "Официальный API с ключом: стабильно и без капч."
    }

private fun SearchSettings.isComplete(): Boolean = when (backend) {
    SearchBackend.HTML_SCRAPE -> true
    SearchBackend.SEARXNG -> searxngUrl.isNotBlank()
    SearchBackend.BRAVE_API -> apiKey.isNotBlank()
}

private fun ToolSettings.trimmed() = copy(
    search = search.copy(searxngUrl = search.searxngUrl.trim(), apiKey = search.apiKey.trim()),
    weather = weather.copy(forecastUrl = weather.forecastUrl.trim(), geocodingUrl = weather.geocodingUrl.trim()),
)

private val BADGE_SIZE = 40.dp
