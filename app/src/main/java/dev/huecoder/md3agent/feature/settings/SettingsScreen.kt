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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Neurology
import com.composables.icons.materialsymbols.rounded.Notifications_active
import com.composables.icons.materialsymbols.rounded.Extension
import com.composables.icons.materialsymbols.rounded.Info
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Dns
import com.composables.icons.materialsymbols.rounded.Face
import com.composables.icons.materialsymbols.rounded.Handyman
import com.composables.icons.materialsymbols.rounded.Shield_person
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Mic
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.BuildConfig
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.feature.main.MainNavBarClearance
import dev.huecoder.md3agent.data.settings.EmbeddingKind
import dev.huecoder.md3agent.assistant.AssistantRole
import dev.huecoder.md3agent.data.settings.AppSettings
import dev.huecoder.md3agent.data.settings.SpeakReplies
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.tools.ToolRegistry
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
    val about: () -> Unit,
    val agent: () -> Unit,
    val memory: () -> Unit,
    val network: () -> Unit,
    val tools: () -> Unit,
    val reminders: () -> Unit,
    val voice: () -> Unit,
    val access: () -> Unit,
    val mcp: () -> Unit,
)

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

    SettingsScaffold(stringResource(R.string.settings_title), onBack = null) { padding ->
        val current = settings ?: return@SettingsScaffold
        val generalEntries = listOf(
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Hub,
                title = stringResource(R.string.settings_models),
                summary = modelsSummary(current),
                onClick = destinations.models,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Palette,
                title = stringResource(R.string.settings_appearance),
                summary = stringResource(R.string.settings_appearance_summary),
                onClick = destinations.appearance,
            ),
        )
        val assistantEntries = listOf(
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Face,
                title = stringResource(R.string.settings_agent),
                summary = stringResource(R.string.settings_agent_summary, current.agent.name),
                onClick = destinations.agent,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Neurology,
                title = stringResource(R.string.settings_memory),
                summary = memorySummary(current),
                onClick = destinations.memory,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Mic,
                title = stringResource(R.string.settings_voice),
                summary = voiceSummary(current),
                onClick = destinations.voice,
            ),
        )
        val capabilityEntries = listOf(
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Handyman,
                title = stringResource(R.string.settings_tools),
                summary = toolsSummary(current, toolRegistry),
                onClick = destinations.tools,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Shield_person,
                title = stringResource(R.string.settings_access),
                summary = accessSummary(current, isAssistant),
                onClick = destinations.access,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Extension,
                title = stringResource(R.string.settings_mcp),
                summary = mcpSummary(current),
                onClick = destinations.mcp,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Notifications_active,
                title = stringResource(R.string.settings_reminders),
                summary = remindersSummary(reminderCount),
                onClick = destinations.reminders,
            ),
        )
        val systemEntries = listOf(
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Dns,
                title = stringResource(R.string.settings_network),
                summary = networkSummary(current),
                onClick = destinations.network,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Info,
                title = stringResource(R.string.settings_about),
                summary = stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME),
                onClick = destinations.about,
            ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = MainNavBarClearance),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_general)) }
            entryGroup(generalEntries)

            item { SectionHeader(stringResource(R.string.settings_section_assistant)) }
            entryGroup(assistantEntries)

            item { SectionHeader(stringResource(R.string.settings_section_capabilities)) }
            entryGroup(capabilityEntries)

            item { SectionHeader(stringResource(R.string.settings_section_system)) }
            entryGroup(systemEntries)
        }
    }
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
    val selection = settings.activeSelection ?: return stringResource(R.string.settings_models_summary_none)
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
