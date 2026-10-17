package com.mwagent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.mwagent.App
import com.mwagent.R
import com.mwagent.ui.avatar.AgentLook
import com.mwagent.ui.components.AgentAvatar
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.ShapeBadge
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.components.groupedListColors
import kotlinx.coroutines.launch

@Composable
fun AgentsScreen(onBack: () -> Unit, onEditAgent: (agentId: String?) -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val repository = (LocalContext.current.applicationContext as App).container.settings
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.settings_agent), onBack = onBack) { padding ->
        val agents = settings?.agents ?: return@SettingsScaffold
        val count = agents.agents.size + 1
        val useForNewChats = stringResource(R.string.agents_use_for_new_chats)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item {
                Text(
                    stringResource(R.string.agents_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                )
            }
            item { SectionHeader(stringResource(R.string.settings_agent)) }
            itemsIndexed(agents.agents, key = { _, agent -> agent.id }) { index, agent ->
                val isActive = agent.id == agents.active.id
                SegmentedListItem(
                    onClick = { onEditAgent(agent.id) },
                    shapes = ListItemDefaults.segmentedShapes(index, count),
                    colors = groupedListColors(),
                    leadingContent = { AgentAvatar(isWorking = false, size = AVATAR_SIZE, look = AgentLook.of(agent)) },
                    supportingContent = if (isActive) {
                        { Text(stringResource(R.string.agents_for_new_chats), color = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    trailingContent = {
                        RadioButton(
                            selected = isActive,
                            onClick = { scope.launch { repository.updateAgents { it.activate(agent.id) } } },
                            modifier = Modifier.semantics { contentDescription = useForNewChats },
                        )
                    },
                ) { Text(agent.name, style = MaterialTheme.typography.titleMediumEmphasized) }
            }
            item(key = "add-agent") {
                SegmentedListItem(
                    onClick = { onEditAgent(null) },
                    shapes = ListItemDefaults.segmentedShapes(count - 1, count),
                    colors = groupedListColors(),
                    leadingContent = {
                        ShapeBadge(
                            MaterialSymbols.Rounded.Add,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                ) { Text(stringResource(R.string.agents_add), style = MaterialTheme.typography.titleMediumEmphasized) }
            }
        }
    }
}

private val AVATAR_SIZE = 40.dp
