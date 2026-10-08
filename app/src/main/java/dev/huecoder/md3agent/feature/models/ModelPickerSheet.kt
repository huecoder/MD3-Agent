package dev.huecoder.md3agent.feature.models

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.rounded.Neurology
import com.composables.icons.materialsymbols.rounded.Refresh
import com.composables.icons.materialsymbols.rounded.Search
import com.composables.icons.materialsymbols.rounded.Visibility
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.llm.models.ModelVendor
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.ui.components.iconRes
import androidx.compose.ui.res.painterResource

@Composable
fun ModelPickerSheet(current: ModelSelection?, onSelect: (ModelSelection) -> Unit, onDismiss: () -> Unit) {
    val viewModel = appViewModel(key = "model-picker") { ModelPickerViewModel(current?.providerId, it.settings, it.modelCatalog) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val capabilities by viewModel.activeCapabilities.collectAsStateWithLifecycle()
    val vendor by viewModel.activeVendor.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentHere = current?.takeIf { it.providerId == state.providerId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION)) {
            PickerHeader(state.totalCount, state.isRefreshing, viewModel::refresh)
            if (state.providers.size > 1) {
                ChipRow {
                    state.providers.forEach { provider ->
                        FilterChip(
                            selected = provider.id == state.providerId,
                            onClick = { viewModel.selectProvider(provider.id) },
                            label = { Text(provider.name) },
                        )
                    }
                }
            }
            SearchField(query, viewModel::search)
            ChipRow {
                CapabilityChip(CapabilityFilter.VISION in capabilities, MaterialSymbols.Rounded.Visibility, R.string.model_filter_vision) { viewModel.toggle(CapabilityFilter.VISION) }
                CapabilityChip(CapabilityFilter.REASONING in capabilities, MaterialSymbols.Rounded.Neurology, R.string.model_filter_reasoning) { viewModel.toggle(CapabilityFilter.REASONING) }
                CapabilityChip(CapabilityFilter.IMAGES in capabilities, MaterialSymbols.Rounded.Image, R.string.model_filter_images) { viewModel.toggle(CapabilityFilter.IMAGES) }
                if (state.vendors.isNotEmpty()) VerticalDivider(Modifier.padding(vertical = 8.dp).size(width = 1.dp, height = 24.dp).align(Alignment.CenterVertically))
                state.vendors.forEach { item -> VendorChip(item, item == vendor) { viewModel.toggle(item) } }
            }
            state.error?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            currentHere?.let { selection ->
                val traits = viewModel.traitsOf(selection)
                if (traits.reasoning) {
                    ReasoningPicker(
                        efforts = traits.reasoningEfforts,
                        selected = selection.reasoningEffort,
                        onSelect = { onSelect(selection.copy(reasoningEffort = it)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            ModelList(
                rows = state.rows,
                selectedId = currentHere?.modelId,
                onPick = { row ->
                    onSelect(viewModel.selection(row, current))
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PickerHeader(count: Int, isRefreshing: Boolean, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.model_picker_title), style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                pluralStringResource(R.plurals.model_picker_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isRefreshing) {
            LoadingIndicator(Modifier.size(40.dp))
        } else {
            IconButton(onClick = onRefresh, shapes = IconButtonDefaults.shapes()) {
                Icon(MaterialSymbols.Rounded.Refresh, contentDescription = stringResource(R.string.model_refresh))
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.model_search)) },
        leadingIcon = { Icon(MaterialSymbols.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.model_search_clear)) }
            }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        content = content,
    )
}

@Composable
private fun CapabilityChip(selected: Boolean, icon: ImageVector, label: Int, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

@Composable
private fun VendorChip(vendor: ModelVendor, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(vendor.displayName) },
        leadingIcon = { Icon(painterResource(vendor.iconRes), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

internal val PickerListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)

private const val SHEET_HEIGHT_FRACTION = 0.92f
