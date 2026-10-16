package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Expand_more
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Sync
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.McpServer
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.mcp.McpServerState
import dev.huecoder.md3agent.mcp.McpTool
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.ToolCatalog
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.components.groupedListColors
import dev.huecoder.md3agent.ui.components.rememberSectionFolds
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.ui.theme.LayoutMotion

@Composable
fun McpServersScreen(onBack: () -> Unit, onEditServer: (serverId: String?) -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val toolbox = (LocalContext.current.applicationContext as App).container.mcp
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val states by toolbox.states.collectAsStateWithLifecycle()
    val collapsed = rememberSectionFolds()
    val modes = rememberToolModes()

    SettingsScaffold(
        title = stringResource(R.string.settings_mcp),
        onBack = onBack,
        actions = {
            IconButton(onClick = { settings?.tools?.mcpServers?.forEach { toolbox.reload(it.id) } }, shapes = IconButtonDefaults.shapes()) {
                Icon(MaterialSymbols.Rounded.Sync, contentDescription = stringResource(R.string.mcp_reconnect))
            }
        },
    ) { padding ->
        val tools = settings?.tools ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_GAP),
        ) {
            item {
                Column {
                    SectionHeader(stringResource(R.string.mcp_section_servers))
                    Text(
                        stringResource(R.string.mcp_servers_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    )
                }
            }
            items(tools.mcpServers, key = { it.id }) { server ->
                val state = states[server.id]
                ServerGroup(
                    server = server,
                    state = state,
                    toolSettings = tools,
                    open = !collapsed.isOpen(server.id),
                    modes = modes,
                    onToggle = { collapsed.toggle(server.id) },
                    onEdit = { onEditServer(server.id) },
                    onChange = { tool, permission -> viewModel.updateTools { it.withPermission(tool.spec.name, permission) } },
                )
            }
            item(key = "add-mcp-server") {
                SegmentedListItem(
                    onClick = { onEditServer(null) },
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
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
internal fun ServerGroup(
    server: McpServer,
    state: McpServerState?,
    toolSettings: ToolSettings,
    open: Boolean,
    modes: ToolModeMemory,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onChange: (McpTool, ToolPermission) -> Unit,
) {
    val haptics = rememberHaptics()
    val tools = (state as? McpServerState.Ready)?.tools.orEmpty().takeIf { server.enabled }.orEmpty()
    val count = tools.size + 1
    val foldState = stringResource(if (open) R.string.common_expanded else R.string.common_collapsed)
    val chevron by animateFloatAsState(if (open) HALF_TURN else 0f, LayoutMotion.settle(), label = "server-chevron")
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            onClick = {
                if (tools.isNotEmpty()) {
                    haptics.tick()
                    onToggle()
                } else {
                    onEdit()
                }
            },
            shapes = ListItemDefaults.segmentedShapes(0, if (open) count else 1),
            colors = groupedListColors(),
            modifier = if (tools.isNotEmpty()) Modifier.semantics { stateDescription = foldState } else Modifier,
            leadingContent = { ShapeBadge(MaterialSymbols.Rounded.Hub) },
            supportingContent = { ServerStatus(server, state, toolSettings) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, shapes = IconButtonDefaults.shapes()) {
                        Icon(MaterialSymbols.Rounded.Edit, contentDescription = stringResource(R.string.mcp_server_edit))
                    }
                    if (tools.isNotEmpty()) {
                        Icon(
                            MaterialSymbols.Rounded.Expand_more,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp).rotate(chevron),
                        )
                    }
                }
            },
        ) { Text(server.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        AnimatedVisibility(
            visible = open && tools.isNotEmpty(),
            enter = expandVertically(LayoutMotion.settle()) + fadeIn(),
            exit = shrinkVertically(LayoutMotion.settle()) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                tools.forEachIndexed { index, tool ->
                    val info = ToolCatalog.of(tool)
                    ToolPermissionCard(
                        toolName = tool.spec.name,
                        icon = info.icon,
                        title = tool.displayName,
                        summary = info.summary.asString(),
                        permission = toolSettings.permission(tool.spec.name, tool.defaultPermission),
                        defaultPermission = tool.defaultPermission,
                        modes = modes,
                        shape = ListItemDefaults.segmentedShapes(index + 1, count).shape,
                        onChange = { onChange(tool, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerStatus(server: McpServer, state: McpServerState?, toolSettings: ToolSettings) {
    val (text, isError) = when {
        !server.enabled -> stringResource(R.string.mcp_status_disabled) to false
        state is McpServerState.Ready -> readyStatus(state, toolSettings) to false
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

@Composable
private fun readyStatus(state: McpServerState.Ready, toolSettings: ToolSettings): String {
    if (state.tools.isEmpty()) return pluralStringResource(R.plurals.mcp_server_found_tools, 0, 0)
    val on = state.tools.count { toolSettings.permission(it.spec.name, it.defaultPermission) != ToolPermission.OFF }
    return stringResource(R.string.tools_category_enabled, on, state.tools.size)
}

private val GROUP_GAP = 12.dp
private const val HALF_TURN = 180f
