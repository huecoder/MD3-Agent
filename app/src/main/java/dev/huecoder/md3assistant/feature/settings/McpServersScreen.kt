package dev.huecoder.md3assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Sync
import dev.huecoder.md3assistant.App
import dev.huecoder.md3assistant.R
import dev.huecoder.md3assistant.data.settings.McpServer
import dev.huecoder.md3assistant.mcp.McpServerState
import dev.huecoder.md3assistant.ui.components.SectionHeader
import dev.huecoder.md3assistant.ui.components.SettingsScaffold
import dev.huecoder.md3assistant.ui.components.ShapeBadge
import dev.huecoder.md3assistant.ui.components.appViewModel
import dev.huecoder.md3assistant.ui.components.groupedListColors

@Composable
fun McpServersScreen(onBack: () -> Unit, onEditServer: (serverId: String?) -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val toolbox = (LocalContext.current.applicationContext as App).container.mcp
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val states by toolbox.states.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(R.string.settings_mcp),
        onBack = onBack,
        actions = {
            IconButton(onClick = { settings?.tools?.mcpServers?.forEach { toolbox.reload(it.id) } }, shapes = IconButtonDefaults.shapes()) {
                Icon(MaterialSymbols.Rounded.Sync, contentDescription = stringResource(R.string.mcp_reconnect))
            }
        },
    ) { padding ->
        val servers = settings?.tools?.mcpServers ?: return@SettingsScaffold
        val count = servers.size + 1
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader(stringResource(R.string.mcp_section_servers)) }
            item {
                Text(
                    stringResource(R.string.mcp_servers_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
            itemsIndexed(servers, key = { _, server -> server.id }) { index, server ->
                SegmentedListItem(
                    onClick = { onEditServer(server.id) },
                    shapes = ListItemDefaults.segmentedShapes(index, count),
                    colors = groupedListColors(),
                    leadingContent = { ShapeBadge(MaterialSymbols.Rounded.Hub) },
                    supportingContent = { ServerStatus(server, states[server.id]) },
                ) { Text(server.name, style = MaterialTheme.typography.titleMediumEmphasized) }
            }
            item(key = "add-mcp-server") {
                SegmentedListItem(
                    onClick = { onEditServer(null) },
                    shapes = ListItemDefaults.segmentedShapes(count - 1, count),
                    colors = groupedListColors(),
                    leadingContent = {
                        ShapeBadge(
                            MaterialSymbols.Rounded.Add,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                ) { Text(stringResource(R.string.mcp_add_server), style = MaterialTheme.typography.titleMediumEmphasized) }
            }
        }
    }
}

@Composable
private fun ServerStatus(server: McpServer, state: McpServerState?) {
    val (text, isError) = when {
        !server.enabled -> stringResource(R.string.mcp_status_disabled) to false
        state is McpServerState.Ready -> pluralStringResource(R.plurals.mcp_server_found_tools, state.tools.size, state.tools.size) to false
        state is McpServerState.Failed -> state.message to true
        state is McpServerState.NeedsSignIn -> stringResource(R.string.mcp_sign_in_needed) to true
        else -> stringResource(R.string.mcp_status_connecting) to false
    }
    Text(
        text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
