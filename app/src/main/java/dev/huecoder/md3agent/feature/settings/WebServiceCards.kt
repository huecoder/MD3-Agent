package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Article
import com.composables.icons.materialsymbols.rounded.Auto_fix_high
import com.composables.icons.materialsymbols.rounded.Data_object
import com.composables.icons.materialsymbols.rounded.Partly_cloudy_day
import com.composables.icons.materialsymbols.rounded.Travel_explore
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.CustomSearch
import dev.huecoder.md3agent.data.settings.FetchBackend
import dev.huecoder.md3agent.data.settings.FetchSettings
import dev.huecoder.md3agent.data.settings.SearchBackend
import dev.huecoder.md3agent.data.settings.SearchMethod
import dev.huecoder.md3agent.data.settings.SearchPresets
import dev.huecoder.md3agent.data.settings.SearchSettings
import dev.huecoder.md3agent.data.settings.WeatherSettings
import dev.huecoder.md3agent.ui.components.FormField
import dev.huecoder.md3agent.ui.components.SectionFolds

internal fun CardGroupScope.searchServiceCards(search: SearchSettings, folds: SectionFolds, onChange: (SearchSettings) -> Unit) {
    card("search-backend") { shape ->
        SettingCard(
            icon = MaterialSymbols.Rounded.Travel_explore,
            title = stringResource(R.string.tools_section_search),
            summary = stringResource(search.backend.hint),
            shape = shape,
            indentBelow = false,
            below = { ChoicePill(SEARCH_CHOICES, search.backend, { onChange(search.copy(backend = it)) }, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
        )
    }
    when (search.backend) {
        SearchBackend.HTML_SCRAPE -> Unit
        SearchBackend.SEARXNG -> card("search-fields") { shape ->
            FieldsCard(shape) {
                FormField(
                    value = search.searxngUrl,
                    onValueChange = { onChange(search.copy(searxngUrl = it)) },
                    label = stringResource(R.string.tools_searxng_url),
                    placeholder = stringResource(R.string.tools_searxng_url_placeholder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SearchBackend.BRAVE_API -> card("search-fields") { shape ->
            FieldsCard(shape) { SecretField(search.apiKey, { onChange(search.copy(apiKey = it)) }, stringResource(R.string.tools_search_api_key)) }
        }
        SearchBackend.CUSTOM -> customSearchCards(search.custom, folds) { onChange(search.copy(custom = it)) }
    }
}

private fun CardGroupScope.customSearchCards(custom: CustomSearch, folds: SectionFolds, onChange: (CustomSearch) -> Unit) {
    card("search-templates") { shape ->
        SettingCard(
            icon = MaterialSymbols.Rounded.Auto_fix_high,
            title = stringResource(R.string.tools_search_custom_templates),
            summary = stringResource(R.string.tools_search_custom_templates_hint),
            shape = shape,
            indentBelow = false,
            below = {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchPresets.all.forEach { preset -> SuggestionChip(onClick = { onChange(preset.applyTo(custom)) }, label = { Text(preset.name) }) }
                }
            },
        )
    }
    card("search-custom-request") { shape ->
        FieldsCard(shape) {
            FormField(
                value = custom.url,
                onValueChange = { onChange(custom.copy(url = it)) },
                label = stringResource(R.string.tools_search_custom_url),
                placeholder = stringResource(R.string.tools_search_custom_url_placeholder),
                supportingText = stringResource(R.string.tools_search_custom_placeholders),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            ChoicePill(METHOD_CHOICES, custom.method, { onChange(custom.copy(method = it)) }, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT)
            if (custom.method == SearchMethod.POST) {
                FormField(
                    value = custom.body,
                    onValueChange = { onChange(custom.copy(body = it)) },
                    label = stringResource(R.string.tools_search_custom_body),
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SecretField(custom.apiKey, { onChange(custom.copy(apiKey = it)) }, stringResource(R.string.tools_search_api_key))
        }
    }
    headersCard(custom.headers, folds.isOpen(HEADERS_FOLD), { folds.toggle(HEADERS_FOLD) }) { onChange(custom.copy(headers = it)) }
    foldCard(
        icon = MaterialSymbols.Rounded.Data_object,
        title = R.string.tools_search_custom_response,
        expanded = folds.isOpen(RESPONSE_FOLD),
        onToggle = { folds.toggle(RESPONSE_FOLD) },
        summary = { stringResource(R.string.tools_search_custom_response_summary, custom.resultsPath.ifBlank { "·" }) },
    ) {
        FormField(custom.resultsPath, { onChange(custom.copy(resultsPath = it)) }, stringResource(R.string.tools_search_custom_results_path), modifier = Modifier.fillMaxWidth())
        FormField(custom.titleField, { onChange(custom.copy(titleField = it)) }, stringResource(R.string.tools_search_custom_title_field), modifier = Modifier.fillMaxWidth())
        FormField(custom.linkField, { onChange(custom.copy(linkField = it)) }, stringResource(R.string.tools_search_custom_link_field), modifier = Modifier.fillMaxWidth())
        FormField(custom.snippetFields, { onChange(custom.copy(snippetFields = it)) }, stringResource(R.string.tools_search_custom_snippet_fields), modifier = Modifier.fillMaxWidth())
    }
}

internal fun CardGroupScope.fetchServiceCards(fetch: FetchSettings, onChange: (FetchSettings) -> Unit) {
    card("fetch-backend") { shape ->
        SettingCard(
            icon = MaterialSymbols.Rounded.Article,
            title = stringResource(R.string.tools_section_fetch),
            summary = stringResource(fetch.backend.hint),
            shape = shape,
            indentBelow = false,
            below = { ChoicePill(FETCH_CHOICES, fetch.backend, { onChange(fetch.copy(backend = it)) }, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
        )
    }
    when (fetch.backend) {
        FetchBackend.DIRECT -> Unit
        FetchBackend.JINA_READER -> card("fetch-fields") { shape ->
            FieldsCard(shape) { SecretField(fetch.jinaApiKey, { onChange(fetch.copy(jinaApiKey = it)) }, stringResource(R.string.tools_fetch_api_key)) }
        }
        FetchBackend.CUSTOM -> card("fetch-fields") { shape ->
            FieldsCard(shape) {
                FormField(
                    value = fetch.customUrl,
                    onValueChange = { onChange(fetch.copy(customUrl = it)) },
                    label = stringResource(R.string.tools_fetch_custom_url),
                    placeholder = stringResource(R.string.tools_fetch_custom_url_placeholder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField(fetch.customApiKey, { onChange(fetch.copy(customApiKey = it)) }, stringResource(R.string.tools_fetch_api_key))
            }
        }
    }
}

internal fun CardGroupScope.weatherServiceCards(weather: WeatherSettings, onChange: (WeatherSettings) -> Unit) {
    card("weather") { shape ->
        FormCard(
            icon = MaterialSymbols.Rounded.Partly_cloudy_day,
            title = stringResource(R.string.tools_weather_title),
            summary = stringResource(R.string.tools_weather_hint),
            shape = shape,
        ) {
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
}

private val SEARCH_CHOICES = SearchBackend.entries.map { Choice(it, it.label) }

private val METHOD_CHOICES = listOf(Choice(SearchMethod.GET, R.string.tools_search_method_get), Choice(SearchMethod.POST, R.string.tools_search_method_post))

private const val HEADERS_FOLD = "search-headers"
private const val RESPONSE_FOLD = "search-response"

private val FETCH_CHOICES = FetchBackend.entries.map { Choice(it, it.label) }

private val SearchBackend.label: Int
    get() = when (this) {
        SearchBackend.HTML_SCRAPE -> R.string.tools_search_backend_html
        SearchBackend.SEARXNG -> R.string.tools_search_backend_searxng
        SearchBackend.BRAVE_API -> R.string.tools_search_backend_brave
        SearchBackend.CUSTOM -> R.string.tools_search_backend_custom
    }

private val SearchBackend.hint: Int
    get() = when (this) {
        SearchBackend.HTML_SCRAPE -> R.string.tools_search_backend_html_hint
        SearchBackend.SEARXNG -> R.string.tools_search_backend_searxng_hint
        SearchBackend.BRAVE_API -> R.string.tools_search_backend_brave_hint
        SearchBackend.CUSTOM -> R.string.tools_search_backend_custom_hint
    }

private val FetchBackend.label: Int
    get() = when (this) {
        FetchBackend.DIRECT -> R.string.tools_fetch_backend_direct
        FetchBackend.JINA_READER -> R.string.tools_fetch_backend_jina
        FetchBackend.CUSTOM -> R.string.tools_fetch_backend_custom
    }

private val FetchBackend.hint: Int
    get() = when (this) {
        FetchBackend.DIRECT -> R.string.tools_fetch_backend_direct_hint
        FetchBackend.JINA_READER -> R.string.tools_fetch_backend_jina_hint
        FetchBackend.CUSTOM -> R.string.tools_fetch_backend_custom_hint
    }
