package dev.huecoder.md3assistant.feature.settings

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.data.settings.SearchBackend
import dev.huecoder.md3assistant.data.settings.SearchSettings
import dev.huecoder.md3assistant.data.settings.ToolPermission
import dev.huecoder.md3assistant.data.settings.ToolSettings
import dev.huecoder.md3assistant.data.settings.WeatherSettings
import dev.huecoder.md3assistant.ui.components.FormField
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import dev.huecoder.md3assistant.ui.components.ShapeBadge
import dev.huecoder.md3assistant.ui.components.ToolCatalog
import dev.huecoder.md3assistant.ui.components.ToolInfo
import dev.huecoder.md3assistant.ui.text.asString
import kotlinx.coroutines.launch

@Composable
fun ToolSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var tools by remember { mutableStateOf<ToolSettings?>(null) }

    LaunchedEffect(Unit) { tools = container.settings.current().tools }

    SettingsScaffold(stringResource(R.string.settings_tools), onBack) { padding ->
        val current = tools ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(stringResource(R.string.tools_section_available))
            container.tools.all.forEach { tool ->
                val name = tool.spec.name
                ToolPermissionRow(
                    info = ToolCatalog.of(name),
                    permission = container.tools.permission(tool, current),
                    onChange = { tools = current.withPermission(name, it) },
                )
            }

            SectionHeader(stringResource(R.string.tools_section_search))
            SearchSection(current.search) { tools = current.copy(search = it) }

            SectionHeader(stringResource(R.string.tools_weather_title))
            WeatherSection(current.weather) { tools = current.copy(weather = it) }

            Button(
                onClick = {
                    scope.launch {
                        container.settings.updateTools { saved -> current.trimmed().copy(access = saved.access) }
                        onBack()
                    }
                },
                enabled = current.search.isComplete(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            ) { Text(stringResource(R.string.settings_save)) }
        }
    }
}

@Composable
private fun ToolPermissionRow(info: ToolInfo, permission: ToolPermission, onChange: (ToolPermission) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShapeBadge(info.icon, size = BADGE_SIZE)
            Column(Modifier.weight(1f)) {
                Text(info.title.asString(), style = MaterialTheme.typography.titleMedium)
                Text(info.summary.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SegmentedChoices(PERMISSION_CHOICES, permission, onChange)
    }
}

private val PERMISSION_CHOICES = listOf(
    Choice(ToolPermission.OFF, R.string.tools_permission_off),
    Choice(ToolPermission.ASK, R.string.tools_permission_ask),
    Choice(ToolPermission.AUTO, R.string.tools_permission_auto),
)

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
                    label = { Text(stringResource(backend.label)) },
                )
            }
        }
        Text(stringResource(search.backend.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (search.backend) {
            SearchBackend.HTML_SCRAPE -> Unit
            SearchBackend.SEARXNG -> FormField(
                value = search.searxngUrl,
                onValueChange = { onChange(search.copy(searxngUrl = it)) },
                label = stringResource(R.string.tools_searxng_url),
                placeholder = stringResource(R.string.tools_searxng_url_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            SearchBackend.BRAVE_API -> FormField(
                value = search.apiKey,
                onValueChange = { onChange(search.copy(apiKey = it)) },
                label = stringResource(R.string.tools_search_api_key),
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
            stringResource(R.string.tools_weather_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormField(
            value = weather.forecastUrl,
            onValueChange = { onChange(weather.copy(forecastUrl = it)) },
            label = stringResource(R.string.tools_weather_forecast_url),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        FormField(
            value = weather.geocodingUrl,
            onValueChange = { onChange(weather.copy(geocodingUrl = it)) },
            label = stringResource(R.string.tools_weather_geocoding_url),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@get:StringRes
private val SearchBackend.label: Int
    get() = when (this) {
        SearchBackend.HTML_SCRAPE -> R.string.tools_search_backend_html
        SearchBackend.SEARXNG -> R.string.tools_search_backend_searxng
        SearchBackend.BRAVE_API -> R.string.tools_search_backend_brave
    }

@get:StringRes
private val SearchBackend.hint: Int
    get() = when (this) {
        SearchBackend.HTML_SCRAPE -> R.string.tools_search_backend_html_hint
        SearchBackend.SEARXNG -> R.string.tools_search_backend_searxng_hint
        SearchBackend.BRAVE_API -> R.string.tools_search_backend_brave_hint
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
