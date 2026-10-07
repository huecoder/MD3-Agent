package dev.huecoder.md3assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Sync
import com.composables.icons.materialsymbols.rounded.Visibility
import com.composables.icons.materialsymbols.rounded.Visibility_off
import dev.huecoder.md3assistant.data.settings.HeaderEntry
import dev.huecoder.md3assistant.ui.components.LabeledSwitch
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.FormField
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import dev.huecoder.md3assistant.ui.components.appViewModel

@Composable
fun ProviderEditorScreen(providerId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "provider-$providerId") { ProviderEditorViewModel(providerId, it.settings, it.modelCatalog) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val fetch by viewModel.modelFetch.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = if (form?.isNew == false) "Провайдер" else "Новый провайдер",
        onBack = onDone,
        actions = {
            if (form?.isNew == false) {
                IconButton(onClick = { viewModel.delete(onDone) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(MaterialSymbols.Rounded.Delete, contentDescription = "Удалить")
                }
            }
        },
    ) { padding ->
        val current = form ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(
                value = current.name,
                onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
                label = "Название",
                modifier = Modifier.fillMaxWidth(),
            )
            FormField(
                value = current.baseUrl,
                onValueChange = { value -> viewModel.edit { it.copy(baseUrl = value) } },
                label = "Base URL",
                placeholder = "https://api.example.com/v1",
                isError = current.baseUrlError != null,
                supportingText = current.baseUrlError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            SecretField(
                value = current.apiKey,
                onValueChange = { value -> viewModel.edit { it.copy(apiKey = value) } },
                label = "API-ключ (Bearer)",
            )

            SectionHeader("Модель")
            ModelPicker(
                selected = current.defaultModel,
                models = current.models,
                onSelect = { value -> viewModel.edit { it.copy(defaultModel = value) } },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = viewModel::fetchModels,
                    enabled = fetch != ModelFetchState.Loading && current.baseUrlError == null && current.baseUrl.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(MaterialSymbols.Rounded.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text("Загрузить модели", Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
                if (fetch == ModelFetchState.Loading) LoadingIndicator(Modifier.size(32.dp))
            }
            (fetch as? ModelFetchState.Failed)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            LabeledSwitch(
                title = "Использовать по умолчанию",
                summary = "Новые чаты начнутся с этой модели",
                checked = current.makeActive,
                onCheckedChange = { value -> viewModel.edit { it.copy(makeActive = value) } },
            )

            SectionHeader("Дополнительные заголовки")
            HeaderEditor(
                headers = current.headers,
                onChange = { headers -> viewModel.edit { it.copy(headers = headers) } },
            )

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = current.canSave,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            ) { Text("Сохранить") }
        }
    }
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    if (revealed) MaterialSymbols.Rounded.Visibility_off else MaterialSymbols.Rounded.Visibility,
                    contentDescription = if (revealed) "Скрыть" else "Показать",
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun HeaderEditor(headers: List<HeaderEntry>, onChange: (List<HeaderEntry>) -> Unit) {
    headers.forEachIndexed { index, header ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(
                value = header.name,
                onValueChange = { value -> onChange(headers.toMutableList().also { it[index] = header.copy(name = value) }) },
                label = "Имя",
                modifier = Modifier.weight(1f),
            )
            FormField(
                value = header.value,
                onValueChange = { value -> onChange(headers.toMutableList().also { it[index] = header.copy(value = value) }) },
                label = "Значение",
                modifier = Modifier.weight(1.4f),
            )
            IconButton(onClick = { onChange(headers.filterIndexed { i, _ -> i != index }) }) {
                Icon(MaterialSymbols.Rounded.Close, contentDescription = "Убрать заголовок")
            }
        }
    }
    TextButton(onClick = { onChange(headers + HeaderEntry("", "")) }) {
        Icon(MaterialSymbols.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text("Добавить заголовок", Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}
