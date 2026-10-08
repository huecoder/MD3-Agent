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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Assistant
import com.composables.icons.materialsymbols.rounded.Chat
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Dns
import com.composables.icons.materialsymbols.rounded.Face
import com.composables.icons.materialsymbols.rounded.Handyman
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.assistant.AssistantRole
import dev.huecoder.md3assistant.data.settings.AppSettings
import dev.huecoder.md3assistant.data.settings.SpeakReplies
import dev.huecoder.md3assistant.data.settings.ToolPermission
import dev.huecoder.md3assistant.tools.ToolRegistry
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
    onOpenVoice: () -> Unit,
    onOpenAccess: () -> Unit,
) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val toolRegistry = (LocalContext.current.applicationContext as App).container.tools
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isAssistant by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isAssistant = AssistantRole.isHeld(context) }

    SettingsScaffold(stringResource(R.string.settings_title), onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        val assistantEntries = listOf(
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Assistant,
                title = stringResource(R.string.settings_default_assistant),
                summary = stringResource(
                    if (isAssistant) R.string.settings_default_assistant_held else R.string.settings_default_assistant_not_held,
                ),
                onClick = { AssistantRole.openSystemPicker(context) },
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Face,
                title = stringResource(R.string.settings_agent),
                summary = stringResource(R.string.settings_agent_summary, current.agent.name),
                onClick = onOpenAgent,
            ),
        )
        val capabilityEntries = listOf(
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Handyman,
                title = stringResource(R.string.settings_tools),
                summary = toolsSummary(current, toolRegistry),
                onClick = onOpenTools,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Terminal,
                title = stringResource(R.string.settings_access),
                summary = accessSummary(current),
                onClick = onOpenAccess,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Chat,
                title = stringResource(R.string.settings_chat),
                summary = chatSummary(current),
                onClick = onOpenChat,
            ),
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Mic,
                title = stringResource(R.string.settings_voice),
                summary = voiceSummary(current),
                onClick = onOpenVoice,
            ),
        )
        val systemEntries = listOf(
            SettingsEntry(
                icon = MaterialSymbols.Rounded.Dns,
                title = stringResource(R.string.settings_network),
                summary = networkSummary(current),
                onClick = onOpenNetwork,
            ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_assistant)) }
            entryGroup(assistantEntries)

            item { SectionHeader(stringResource(R.string.settings_section_capabilities)) }
            entryGroup(capabilityEntries)

            item { SectionHeader(stringResource(R.string.settings_section_providers)) }
            providerItems(current, onEditProvider)

            item { SectionHeader(stringResource(R.string.settings_section_system)) }
            entryGroup(systemEntries)
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

@Composable
private fun toolsSummary(settings: AppSettings, registry: ToolRegistry): String =
    stringResource(R.string.settings_tools_summary, registry.all.count { registry.permission(it, settings.tools) != ToolPermission.OFF }, registry.all.size)

@Composable
private fun accessSummary(settings: AppSettings): String = stringResource(settings.tools.access.shell.title)

@Composable
private fun chatSummary(settings: AppSettings): String =
    stringResource(if (settings.chat.showTokenUsage) R.string.settings_tokens_shown else R.string.settings_tokens_hidden)

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
