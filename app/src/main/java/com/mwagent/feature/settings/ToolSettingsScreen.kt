package com.mwagent.feature.settings

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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mwagent.App
import com.mwagent.R
import com.mwagent.data.settings.FetchBackend
import com.mwagent.data.settings.FetchSettings
import com.mwagent.data.settings.SearchBackend
import com.mwagent.data.settings.SearchSettings
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.data.settings.WeatherSettings
import com.mwagent.ui.components.FormField
import com.mwagent.ui.components.FoldableHeader
import com.mwagent.ui.components.rememberSectionFolds
import com.mwagent.ui.theme.LayoutMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.mwagent.ui.components.SaveAction
import com.mwagent.ui.components.SearchField
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.ShapeBadge
import com.mwagent.ui.components.ToolCatalog
import com.mwagent.ui.components.ToolCategory
import com.mwagent.ui.components.ToolInfo
import com.mwagent.ui.text.asString
import kotlinx.coroutines.launch

@Composable
fun ToolSettingsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    var tools by remember { mutableStateOf<ToolSettings?>(null) }
    var saved by remember { mutableStateOf<ToolSettings?>(null) }

    LaunchedEffect(Unit) { container.settings.current().tools.let { saved = it.trimmed(); tools = it } }

    val save = tools?.let { current ->
        val draft = current.trimmed()
        SaveAction(isDirty = draft != saved, canSave = current.search.isComplete() && current.fetch.isComplete()) {
            scope.launch {
                container.settings.updateTools { persisted -> draft.copy(access = persisted.access, phoneUse = persisted.phoneUse) }
                saved = draft
                onBack()
            }
        }
    }

    val folds = rememberSectionFolds(*ToolCategory.entries.map { it.name }.toTypedArray())
    var query by rememberSaveable { mutableStateOf("") }

    SettingsScaffold(stringResource(R.string.settings_tools), onBack, save = save) { padding ->
        val current = tools ?: return@SettingsScaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, stringResource(R.string.tools_search_hint), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ToolPermissionGroups(
                    tools = container.tools.all,
                    permissionOf = { container.tools.permission(it, current) },
                    onChange = { tool, permission -> tools = current.withPermission(tool.spec.name, permission) },
                    folds = folds,
                    query = query,
                ) { category ->
                    if (category == ToolCategory.WEB) {
                        ServiceCard(R.string.tools_section_search, Modifier.padding(top = 12.dp)) {
                            SearchSection(current.search) { tools = current.copy(search = it) }
                        }
                        ServiceCard(R.string.tools_section_fetch) {
                            FetchSection(current.fetch) { tools = current.copy(fetch = it) }
                        }
                        ServiceCard(R.string.tools_weather_title) {
                            WeatherSection(current.weather) { tools = current.copy(weather = it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(@StringRes title: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMediumEmphasized)
            content()
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
private fun FetchSection(fetch: FetchSettings, onChange: (FetchSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedChoices(FETCH_CHOICES, fetch.backend, onSelect = { onChange(fetch.copy(backend = it)) })
        Text(stringResource(fetch.backend.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (fetch.backend) {
            FetchBackend.DIRECT -> Unit
            FetchBackend.JINA_READER -> SecretField(fetch.jinaApiKey) { onChange(fetch.copy(jinaApiKey = it)) }
            FetchBackend.CUSTOM -> {
                FormField(
                    value = fetch.customUrl,
                    onValueChange = { onChange(fetch.copy(customUrl = it)) },
                    label = stringResource(R.string.tools_fetch_custom_url),
                    placeholder = stringResource(R.string.tools_fetch_custom_url_placeholder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField(fetch.customApiKey) { onChange(fetch.copy(customApiKey = it)) }
            }
        }
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit) {
    FormField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.tools_fetch_api_key),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val FETCH_CHOICES = listOf(
    Choice(FetchBackend.DIRECT, R.string.tools_fetch_backend_direct),
    Choice(FetchBackend.JINA_READER, R.string.tools_fetch_backend_jina),
    Choice(FetchBackend.CUSTOM, R.string.tools_fetch_backend_custom),
)

@get:StringRes
private val FetchBackend.hint: Int
    get() = when (this) {
        FetchBackend.DIRECT -> R.string.tools_fetch_backend_direct_hint
        FetchBackend.JINA_READER -> R.string.tools_fetch_backend_jina_hint
        FetchBackend.CUSTOM -> R.string.tools_fetch_backend_custom_hint
    }

private fun FetchSettings.isComplete(): Boolean = backend != FetchBackend.CUSTOM || customUrl.isNotBlank()

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
    fetch = fetch.copy(jinaApiKey = fetch.jinaApiKey.trim(), customUrl = fetch.customUrl.trim(), customApiKey = fetch.customApiKey.trim()),
)

