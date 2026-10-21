package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chevron_right
import dev.huecoder.md3agent.feature.mode.titleRes
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.BuildConfig
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.main.MainNavBarClearance
import dev.huecoder.md3agent.data.settings.EmbeddingKind
import dev.huecoder.md3agent.assistant.AssistantRole
import dev.huecoder.md3agent.data.settings.AppSettings
import dev.huecoder.md3agent.data.settings.ModeSettings
import dev.huecoder.md3agent.data.settings.SpeakReplies
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.tools.ToolRegistry
import dev.huecoder.md3agent.ui.components.SearchField
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.components.groupedListColors
import kotlinx.coroutines.flow.map

@Immutable
data class SettingsDestinations(
    val models: () -> Unit,
    val appearance: () -> Unit,
    val chat: () -> Unit,
    val about: () -> Unit,
    val agent: () -> Unit,
    val memory: () -> Unit,
    val network: () -> Unit,
    val tools: () -> Unit,
    val reminders: () -> Unit,
    val voice: () -> Unit,
    val access: () -> Unit,
    val notifications: () -> Unit,
    val assistantInvoke: () -> Unit,
    val phoneUse: () -> Unit,
    val mcp: () -> Unit,
    val mode: () -> Unit,
    val editAgent: (agentId: String?) -> Unit,
) {
    fun open(page: SettingsPage, settings: AppSettings) = when (page) {
        SettingsPage.MODELS -> models()
        SettingsPage.MODE -> mode()
        SettingsPage.AGENT -> if (settings.mode.multipleAgents) agent() else editAgent(settings.agents.active.id)
        SettingsPage.MEMORY -> memory()
        SettingsPage.CHAT -> chat()
        SettingsPage.APPEARANCE -> appearance()
        SettingsPage.VOICE -> voice()
        SettingsPage.TOOLS -> tools()
        SettingsPage.MCP -> mcp()
        SettingsPage.PHONE_USE -> phoneUse()
        SettingsPage.ACCESS -> access()
        SettingsPage.ASSISTANT_INVOKE -> assistantInvoke()
        SettingsPage.NOTIFICATIONS -> notifications()
        SettingsPage.REMINDERS -> reminders()
        SettingsPage.NETWORK -> network()
        SettingsPage.ABOUT -> about()
    }
}

@Composable
fun SettingsScreen(destinations: SettingsDestinations) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val container = (LocalContext.current.applicationContext as App).container
    val toolRegistry = container.tools
    val reminderCount by remember(container) { container.reminders.observeAll().map { it.size } }.collectAsStateWithLifecycle(initialValue = 0)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isAssistant by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isAssistant = AssistantRole.isHeld(context) }
    var query by rememberSaveable { mutableStateOf("") }

    SettingsScaffold(stringResource(R.string.settings_title), onBack = null) { padding ->
        val current = settings ?: return@SettingsScaffold
        val available = SettingsPage.entries.filter { it.isAvailable(current.mode) }
        val rows = searchRows(available, current.mode)
        val results = SettingsSearch.filter(rows, query)
        val groups = SettingsSection.entries.map { section ->
            section to available.filter { it.section == section }.map { page ->
                SettingsEntry(
                    icon = page.icon,
                    title = stringResource(page.titleFor(current.mode)),
                    summary = pageSummary(page, current, toolRegistry, isAssistant, reminderCount),
                    onClick = { destinations.open(page, current) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = MainNavBarClearance),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SearchField(query, { query = it }, stringResource(R.string.settings_search_hint), Modifier.padding(vertical = 8.dp)) }
            if (query.isBlank()) {
                groups.forEach { (section, entries) ->
                    item(key = section) { SectionHeader(stringResource(section.title)) }
                    entryGroup(entries)
                }
            } else if (results.isEmpty()) {
                item { Text(stringResource(R.string.settings_search_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            } else {
                itemsIndexed(results, key = { _, row -> "${row.page}/${row.path}/${row.title}" }) { index, row ->
                    SegmentedListItem(
                        onClick = { destinations.open(row.page, current) },
                        shapes = ListItemDefaults.segmentedShapes(index, results.size),
                        colors = groupedListColors(),
                        leadingContent = { ShapeBadge(row.page.icon) },
                        supportingContent = { Text(row.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    ) { Text(row.title, style = MaterialTheme.typography.titleMediumEmphasized) }
                }
            }
        }
    }
}

@Composable
private fun searchRows(pages: List<SettingsPage>, mode: ModeSettings): List<SettingsSearchRow> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(pages, mode, configuration) {
        val pageRows = pages.map { page ->
            SettingsSearchRow(page, context.getString(page.titleFor(mode)), context.getString(page.section.title))
        }
        val itemRows = SETTINGS_ITEMS.filter { it.page in pages }.map { item ->
            val pageTitle = context.getString(item.page.titleFor(mode))
            val path = item.section?.let { "$pageTitle › ${context.getString(it)}" } ?: pageTitle
            SettingsSearchRow(item.page, context.getString(item.title), path)
        }
        pageRows + itemRows
    }
}

@Composable
private fun pageSummary(page: SettingsPage, settings: AppSettings, registry: ToolRegistry, isAssistant: Boolean, reminderCount: Int): String = when (page) {
    SettingsPage.MODELS -> modelsSummary(settings)
    SettingsPage.MODE -> stringResource(settings.mode.preset?.titleRes ?: R.string.mode_custom)
    SettingsPage.AGENT -> stringResource(R.string.settings_agent_summary, settings.agents.active.name)
    SettingsPage.MEMORY -> memorySummary(settings)
    SettingsPage.CHAT -> stringResource(R.string.settings_chat_summary)
    SettingsPage.APPEARANCE -> stringResource(R.string.settings_appearance_summary)
    SettingsPage.VOICE -> voiceSummary(settings)
    SettingsPage.TOOLS -> toolsSummary(settings, registry)
    SettingsPage.MCP -> mcpSummary(settings)
    SettingsPage.PHONE_USE -> stringResource(R.string.settings_phone_use_summary)
    SettingsPage.ACCESS -> accessSummary(settings, isAssistant)
    SettingsPage.ASSISTANT_INVOKE -> stringResource(R.string.settings_assistant_invoke_summary)
    SettingsPage.NOTIFICATIONS -> stringResource(R.string.settings_notifications_summary)
    SettingsPage.REMINDERS -> remindersSummary(reminderCount)
    SettingsPage.NETWORK -> networkSummary(settings)
    SettingsPage.ABOUT -> stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME)
}

@Composable
private fun memorySummary(settings: AppSettings): String {
    val memory = settings.tools.memory
    return when (memory.embeddingKind) {
        EmbeddingKind.OFF -> stringResource(R.string.settings_memory_summary_off)
        EmbeddingKind.GEMINI -> stringResource(R.string.settings_memory_summary_gemini)
        EmbeddingKind.OPENAI -> stringResource(R.string.settings_memory_summary_openai, memory.model)
    }
}

@Composable
private fun toolsSummary(settings: AppSettings, registry: ToolRegistry): String =
    stringResource(R.string.settings_tools_summary, registry.all.count { registry.permission(it, settings.tools) != ToolPermission.OFF }, registry.all.size)

@Composable
private fun accessSummary(settings: AppSettings, isAssistant: Boolean): String =
    if (isAssistant) {
        stringResource(R.string.settings_access_summary, stringResource(settings.tools.access.shell.title), stringResource(settings.tools.access.files.title))
    } else {
        stringResource(R.string.settings_access_summary_not_assistant)
    }

@Composable
private fun mcpSummary(settings: AppSettings): String {
    val enabled = settings.tools.mcpServers.count { it.enabled }
    return if (enabled == 0) stringResource(R.string.settings_mcp_summary_none) else pluralStringResource(R.plurals.settings_mcp_summary, enabled, enabled)
}

@Composable
private fun modelsSummary(settings: AppSettings): String {
    val selection = settings.activeSelection
        ?: return stringResource(if (settings.providers.isEmpty()) R.string.settings_models_summary_none else R.string.settings_models_summary_pick)
    val provider = settings.provider(selection.providerId)?.name.orEmpty()
    return stringResource(R.string.settings_models_summary, selection.modelId, provider)
}

@Composable
private fun voiceSummary(settings: AppSettings): String {
    val provider = settings.voice.transcriptionProvider?.name ?: stringResource(R.string.settings_voice_no_provider)
    val speak = stringResource(
        when (settings.voice.speakReplies) {
            SpeakReplies.NEVER -> R.string.settings_voice_speak_never
            SpeakReplies.AFTER_VOICE_INPUT -> R.string.settings_voice_speak_after_voice
            SpeakReplies.ALWAYS -> R.string.settings_voice_speak_always
        },
    )
    return stringResource(R.string.settings_voice_summary, provider, speak)
}

@Composable
private fun remindersSummary(count: Int): String =
    if (count == 0) stringResource(R.string.settings_reminders_none) else pluralStringResource(R.plurals.settings_reminders_upcoming, count, count)

@Composable
private fun networkSummary(settings: AppSettings): String =
    if (settings.network.dohEnabled && settings.network.dohUrl.isNotBlank()) {
        stringResource(R.string.settings_network_summary_doh, settings.network.dohUrl)
    } else {
        stringResource(R.string.settings_network_summary_system)
    }

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
