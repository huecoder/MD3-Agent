package dev.huecoder.md3assistant.feature.settings

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Assistant
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Dns
import com.composables.icons.materialsymbols.rounded.Face
import com.composables.icons.materialsymbols.rounded.Handyman
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.assistant.AssistantRole
import dev.huecoder.md3assistant.data.settings.AppSettings
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import dev.huecoder.md3assistant.ui.components.ShapeBadge
import dev.huecoder.md3assistant.ui.components.appViewModel
import dev.huecoder.md3assistant.ui.components.groupedListColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProvider: (providerId: String?) -> Unit,
    onOpenAgent: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val toolNames = (LocalContext.current.applicationContext as App).container.tools.all.map { it.spec.name }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isAssistant by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isAssistant = AssistantRole.isHeld(context) }

    SettingsScaffold("Настройки", onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader("Ассистент") }
            entryGroup(
                listOf(
                    SettingsEntry(
                        icon = MaterialSymbols.Rounded.Assistant,
                        title = "Ассистент по умолчанию",
                        summary = if (isAssistant) "Включён, вызов зажатием кнопки" else "Не назначен, нажми чтобы выбрать",
                        onClick = { AssistantRole.openSystemPicker(context) },
                    ),
                    SettingsEntry(
                        icon = MaterialSymbols.Rounded.Face,
                        title = "Агент",
                        summary = "${current.agent.name} · системный промпт",
                        onClick = onOpenAgent,
                    ),
                ),
            )

            item { SectionHeader("Возможности") }
            entryGroup(
                listOf(
                    SettingsEntry(
                        icon = MaterialSymbols.Rounded.Handyman,
                        title = "Инструменты",
                        summary = toolsSummary(current, toolNames),
                        onClick = onOpenTools,
                    ),
                    SettingsEntry(
                        icon = MaterialSymbols.Rounded.Mic,
                        title = "Чат и голос",
                        summary = chatSummary(current),
                        onClick = onOpenChat,
                    ),
                ),
            )

            item { SectionHeader("Провайдеры") }
            providerItems(current, onEditProvider)

            item { SectionHeader("Система") }
            entryGroup(
                listOf(
                    SettingsEntry(
                        icon = MaterialSymbols.Rounded.Dns,
                        title = "Сеть",
                        summary = networkSummary(current),
                        onClick = onOpenNetwork,
                    ),
                ),
            )
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
                { Icon(MaterialSymbols.RoundedFilled.Check_circle, contentDescription = "Активный", tint = MaterialTheme.colorScheme.primary) }
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
        ) { Text("Добавить провайдера", style = MaterialTheme.typography.titleMediumEmphasized) }
    }
}

private fun toolsSummary(settings: AppSettings, toolNames: List<String>): String =
    "Включено ${toolNames.count(settings.tools::isEnabled)} из ${toolNames.size} · поиск, погода, экран"

private fun chatSummary(settings: AppSettings): String {
    val speechProvider = settings.provider(settings.speech.providerId)?.name ?: "как у чата"
    val tokens = if (settings.chat.showTokenUsage) "токены видны" else "токены скрыты"
    return "Диктовка: ${settings.speech.model} · $speechProvider · $tokens"
}

private fun networkSummary(settings: AppSettings): String =
    if (settings.network.dohEnabled && settings.network.dohUrl.isNotBlank()) "DoH: ${settings.network.dohUrl}" else "Системный DNS"

data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val onClick: () -> Unit,
)

private fun LazyListScope.entryGroup(entries: List<SettingsEntry>) {
    itemsIndexed(entries, key = { _, entry -> entry.title }) { index, entry ->
        SegmentedListItem(
            onClick = entry.onClick,
            shapes = ListItemDefaults.segmentedShapes(index, entries.size),
            colors = groupedListColors(),
            leadingContent = { ShapeBadge(entry.icon) },
            supportingContent = { Text(entry.summary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Icon(MaterialSymbols.Rounded.Chevron_right, contentDescription = null) },
        ) { Text(entry.title, style = MaterialTheme.typography.titleMediumEmphasized) }
    }
}
