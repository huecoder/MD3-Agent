package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.ui.components.SaveAction
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun ProviderEditorScreen(providerId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "provider-$providerId") { ProviderEditorViewModel(providerId, it.settings, it.modelCatalog, it.applicationScope) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    var headersOpen by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(if (form?.isNew == false) R.string.provider_title else R.string.provider_title_new),
        onBack = onDone,
        save = SaveAction(isDirty, form?.canSave == true) { viewModel.save(onDone) },
        actions = {
            if (form?.isNew == false) {
                IconButton(onClick = { viewModel.delete(onDone) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(R.string.provider_delete))
                }
            }
        },
    ) { padding ->
        val current = form ?: return@SettingsScaffold
        Column(Modifier.settingsPage(padding), verticalArrangement = Arrangement.spacedBy(EDITOR_GROUP_GAP)) {
            CardGroup {
                card { shape ->
                    FormatCard(
                        formats = ApiFormat.CHAT,
                        selected = current.format,
                        hint = stringResource(R.string.provider_format_hint),
                        onSelect = { format -> viewModel.edit { it.withFormat(format) } },
                        shape = shape,
                    )
                }
                card { shape ->
                    EndpointCard(
                        name = current.name,
                        onName = { value -> viewModel.edit { it.copy(name = value) } },
                        baseUrl = current.baseUrl,
                        onBaseUrl = { value -> viewModel.edit { it.copy(baseUrl = value) } },
                        baseUrlError = current.baseUrlError,
                        defaultBaseUrl = current.format.defaultBaseUrl,
                        apiKey = current.apiKey,
                        onApiKey = { value -> viewModel.edit { it.copy(apiKey = value) } },
                        shape = shape,
                    )
                }
            }
            CardGroup { headersCard(current.headers, headersOpen, { headersOpen = !headersOpen }) { headers -> viewModel.edit { it.copy(headers = headers) } } }
        }
    }
}
