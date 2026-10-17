package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Article
import com.composables.icons.materialsymbols.rounded.Partly_cloudy_day
import com.composables.icons.materialsymbols.rounded.Travel_explore
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.FetchBackend
import dev.huecoder.md3agent.data.settings.FetchSettings
import dev.huecoder.md3agent.data.settings.SearchBackend
import dev.huecoder.md3agent.data.settings.SearchSettings
import dev.huecoder.md3agent.data.settings.WeatherSettings
import dev.huecoder.md3agent.ui.components.FormField

internal fun CardGroupScope.searchServiceCards(search: SearchSettings, onChange: (SearchSettings) -> Unit) {
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

private val FETCH_CHOICES = FetchBackend.entries.map { Choice(it, it.label) }

private val SearchBackend.label: Int
    get() = when (this) {
        SearchBackend.HTML_SCRAPE -> R.string.tools_search_backend_html
        SearchBackend.SEARXNG -> R.string.tools_search_backend_searxng
        SearchBackend.BRAVE_API -> R.string.tools_search_backend_brave
    }

private val SearchBackend.hint: Int
    get() = when (this) {
        SearchBackend.HTML_SCRAPE -> R.string.tools_search_backend_html_hint
        SearchBackend.SEARXNG -> R.string.tools_search_backend_searxng_hint
        SearchBackend.BRAVE_API -> R.string.tools_search_backend_brave_hint
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
