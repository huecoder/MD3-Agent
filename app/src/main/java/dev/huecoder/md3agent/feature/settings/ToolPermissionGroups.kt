package dev.huecoder.md3agent.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.ui.components.CheckSwitch
import dev.huecoder.md3agent.ui.components.FoldableHeader
import dev.huecoder.md3agent.ui.components.SectionFolds
import dev.huecoder.md3agent.ui.components.ShapeBadge
import dev.huecoder.md3agent.ui.components.ToolCatalog
import dev.huecoder.md3agent.ui.components.ToolCategory
import dev.huecoder.md3agent.ui.components.ToolInfo
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.ui.theme.LayoutMotion

@Composable
fun ToolPermissionGroups(
    tools: List<Tool>,
    permissionOf: (Tool) -> ToolPermission,
    onChange: (Tool, ToolPermission) -> Unit,
    folds: SectionFolds,
    query: String = "",
    extras: @Composable (ToolCategory) -> Unit = {},
) {
    val lastModes = remember { mutableStateMapOf<String, ToolPermission>() }
    val searching = query.isNotBlank()
    val sections = toolSections(tools, query)
    if (sections.isEmpty()) {
        Text(
            stringResource(R.string.tools_search_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = EMPTY_PADDING),
        )
        return
    }
    sections.forEach { section ->
        val open = searching || folds.isOpen(section.category.name)
        FoldableHeader(
            title = section.title,
            expanded = open,
            onToggle = { if (!searching) folds.toggle(section.category.name) },
            summary = stringResource(R.string.tools_category_enabled, section.all.count { permissionOf(it.tool) != ToolPermission.OFF }, section.all.size),
        )
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(LayoutMotion.settle()) + fadeIn(),
            exit = shrinkVertically(LayoutMotion.settle()) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                section.shown.forEachIndexed { index, entry ->
                    val name = entry.tool.spec.name
                    val permission = permissionOf(entry.tool)
                    val mode = modeBehind(permission, lastModes[name], entry.tool.defaultPermission)
                    ToolRow(
                        entry = entry,
                        enabled = permission != ToolPermission.OFF,
                        mode = mode,
                        shape = ListItemDefaults.segmentedShapes(index, section.shown.size).shape,
                        onMode = {
                            lastModes[name] = it
                            onChange(entry.tool, it)
                        },
                        onEnabled = { on ->
                            if (!on) lastModes[name] = mode
                            onChange(entry.tool, if (on) mode else ToolPermission.OFF)
                        },
                    )
                }
                if (!searching) extras(section.category)
            }
        }
    }
}

private class ToolEntry(val tool: Tool, val info: ToolInfo, val title: String, val summary: String)

private class ToolSection(val category: ToolCategory, val title: String, val all: List<ToolEntry>, val shown: List<ToolEntry>)

@Composable
private fun toolSections(tools: List<Tool>, query: String): List<ToolSection> {
    val byCategory = tools
        .map { tool -> ToolCatalog.of(tool).let { ToolEntry(tool, it, it.title.asString(), it.summary.asString()) } }
        .groupBy { it.info.category }
    return ToolCategory.entries.mapNotNull { category ->
        val all = byCategory[category] ?: return@mapNotNull null
        val title = stringResource(category.title)
        val shown = all.filter { matchesSearch(query, title, it.title, it.summary, it.tool.spec.name) }
        ToolSection(category, title, all, shown).takeIf { shown.isNotEmpty() }
    }
}

@Composable
private fun ToolRow(
    entry: ToolEntry,
    enabled: Boolean,
    mode: ToolPermission,
    shape: Shape,
    onMode: (ToolPermission) -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    val dim by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, LayoutMotion.settle(), label = "tool-dim")
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        ToolCardLayout(
            modifier = Modifier.padding(horizontal = ROW_PADDING, vertical = ROW_PADDING_VERTICAL),
            icon = { ShapeBadge(entry.info.icon, size = BADGE_SIZE, modifier = Modifier.alpha(dim)) },
            title = {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(dim),
                )
            },
            summary = entry.summary.takeIf { it.isNotBlank() }?.let { text ->
                {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(dim),
                    )
                }
            },
            mode = { ModePill(mode = mode, enabled = enabled, onSelect = onMode) },
            toggle = {
                CheckSwitch(
                    checked = enabled,
                    onCheckedChange = {
                        haptics.toggle(it)
                        onEnabled(it)
                    },
                    modifier = Modifier.semantics { contentDescription = entry.title },
                )
            },
        )
    }
}

private val BADGE_SIZE = 32.dp
private val ROW_PADDING = 16.dp
private val ROW_PADDING_VERTICAL = 12.dp
private val EMPTY_PADDING = 48.dp
private const val DISABLED_ALPHA = 0.38f
private const val TITLE_MAX_LINES = 2
