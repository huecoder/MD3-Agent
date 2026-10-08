package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.AppSettings
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.components.groupedListColors

@Composable
fun ModelsScreen(onBack: () -> Unit, onEditProvider: (providerId: String?) -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_models), onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_providers)) }
            providerItems(current, onEditProvider)
        }
    }
}

private fun LazyListScope.providerItems(settings: AppSettings, onEditProvider: (String?) -> Unit) {
    val count = settings.providers.size + 1
    itemsIndexed(settings.providers, key = { _, provider -> provider.id }) { index, provider ->
        val isActive = settings.activeSelection?.providerId == provider.id
        SegmentedListItem(
            onClick = { onEditProvider(provider.id) },
            shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = groupedListColors(),
            leadingContent = { ShapeBadge(MaterialSymbols.Rounded.Hub) },
            supportingContent = {
                Text(
                    if (isActive) settings.activeSelection?.modelId.orEmpty() else provider.baseUrl,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = if (isActive) {
                { Icon(MaterialSymbols.RoundedFilled.Check_circle, contentDescription = stringResource(R.string.settings_provider_active), tint = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
        ) { Text(provider.name, style = MaterialTheme.typography.titleMediumEmphasized) }
    }
    item(key = "add-provider") {
        SegmentedListItem(
            onClick = { onEditProvider(null) },
            shapes = ListItemDefaults.segmentedShapes(count - 1, count),
            colors = groupedListColors(),
            leadingContent = {
                ShapeBadge(
                    MaterialSymbols.Rounded.Add,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            },
        ) { Text(stringResource(R.string.settings_add_provider), style = MaterialTheme.typography.titleMediumEmphasized) }
    }
}
