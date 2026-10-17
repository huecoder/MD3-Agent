package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Api
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.composables.icons.materialsymbols.rounded.Network_check
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.composables.icons.materialsymbols.rounded.Sync
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.HeaderEntry
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.ui.components.FormField
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString

@Composable
internal fun FormatCard(formats: List<ApiFormat>, selected: ApiFormat, hint: String, onSelect: (ApiFormat) -> Unit, shape: Shape) {
    SettingCard(
        icon = MaterialSymbols.Rounded.Api,
        title = stringResource(R.string.provider_format),
        summary = hint,
        shape = shape,
        indentBelow = false,
        below = { ChoicePill(apiFormatChoices(formats), selected, onSelect, Modifier.fillMaxWidth(), fillWidth = true, height = CHOICE_HEIGHT) },
    )
}

@Composable
internal fun EndpointCard(
    name: String,
    onName: (String) -> Unit,
    baseUrl: String,
    onBaseUrl: (String) -> Unit,
    baseUrlError: UiText?,
    defaultBaseUrl: String,
    apiKey: String,
    onApiKey: (String) -> Unit,
    shape: Shape,
) {
    FieldsCard(shape) {
        FormField(
            value = name,
            onValueChange = onName,
            label = stringResource(R.string.provider_name),
            modifier = Modifier.fillMaxWidth(),
        )
        FormField(
            value = baseUrl,
            onValueChange = onBaseUrl,
            label = stringResource(R.string.provider_base_url),
            placeholder = defaultBaseUrl,
            isError = baseUrlError != null,
            supportingText = baseUrlError?.asString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(value = apiKey, onValueChange = onApiKey, label = stringResource(R.string.provider_api_key))
    }
}

@Composable
internal fun CopyFromChatCard(providers: List<ProviderProfile>, onCopy: (ProviderProfile) -> Unit, shape: Shape) {
    SettingCard(
        icon = MaterialSymbols.Rounded.Content_copy,
        title = stringResource(R.string.voice_provider_copy_from_chat),
        summary = stringResource(R.string.voice_provider_copy_summary),
        shape = shape,
        indentBelow = false,
        below = {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { source -> SuggestionChip(onClick = { onCopy(source) }, label = { Text(source.name) }) }
            }
        },
    )
}

internal fun CardGroupScope.headersCard(headers: List<HeaderEntry>, expanded: Boolean, onToggle: () -> Unit, onChange: (List<HeaderEntry>) -> Unit) {
    foldCard(
        icon = MaterialSymbols.Rounded.Code,
        title = R.string.provider_section_headers,
        expanded = expanded,
        onToggle = onToggle,
        summary = { if (headers.isEmpty()) stringResource(R.string.provider_headers_none) else pluralStringResource(R.plurals.provider_headers_count, headers.size, headers.size) },
    ) { HeaderEditor(headers, onChange) }
}

@Composable
internal fun TestCard(
    title: String,
    summary: String,
    summaryColor: Color?,
    running: Boolean,
    enabled: Boolean,
    onTest: () -> Unit,
    shape: Shape,
) {
    SettingCard(
        icon = MaterialSymbols.Rounded.Network_check,
        title = title,
        summary = summary,
        summaryColor = summaryColor,
        shape = shape,
        onClick = onTest.takeIf { enabled && !running },
        trailing = {
            if (running) {
                LoadingIndicator(Modifier.size(TEST_INDICATOR_SIZE))
            } else {
                Icon(MaterialSymbols.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
internal fun LinkCard(icon: ImageVector, title: String, summary: String, onClick: () -> Unit, shape: Shape) {
    SettingCard(
        icon = icon,
        title = title,
        summary = summary,
        shape = shape,
        onClick = onClick,
        trailing = { Icon(MaterialSymbols.Rounded.Open_in_new, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

private val TEST_INDICATOR_SIZE = 32.dp
